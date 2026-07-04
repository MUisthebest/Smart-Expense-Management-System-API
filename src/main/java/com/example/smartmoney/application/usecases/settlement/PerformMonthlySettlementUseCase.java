package com.example.smartmoney.application.usecases.settlement;

import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectHistory;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.repositories.ProjectHistoryRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectTrackingRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.services.SafeSpendingService;
import com.example.smartmoney.domain.services.SettlementEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerformMonthlySettlementUseCase {

    private final ProjectRepository projectRepository;
    private final ProjectTrackingRepository projectTrackingRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final ProjectHistoryRepository projectHistoryRepository;
    private final SafeSpendingService safeSpendingService;

    public void execute() {
        List<Project> activeProjects = projectRepository.findAllByStatus(ProjectStatus.ACTIVE);
        log.info("[Settlement] Starting monthly settlement for {} active project(s).", activeProjects.size());

        Map<UserId, List<Project>> userProjects = activeProjects.stream()
                .collect(Collectors.groupingBy(Project::getOwnerId));

        for (Map.Entry<UserId, List<Project>> entry : userProjects.entrySet()) {
            try {
                settleUserProjects(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.error("[Settlement] Failed to settle projects for user {}: {}",
                        entry.getKey().value(), e.getMessage(), e);
            }
        }

        log.info("[Settlement] Monthly settlement complete.");
    }

    @Transactional
    public void settleUserProjects(UserId ownerId, List<Project> projects) {
        UUID ownerUuid = ownerId.value();

        User user = userRepository.findById(ownerUuid)
                .orElseThrow(() -> new ProjectException(
                        "User not found",
                        ProjectErrorCode.PROJECT_PERMISSION_DENIED
                ));

        if (!user.hasCompletedFinancialSetup()) {
            log.warn("[Settlement] Financial setup not complete for user {}. Skipping.", ownerUuid);
            return;
        }

        List<Project> validProjects = projects.stream()
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE || p.getStatus() == ProjectStatus.FROZEN)
                .toList();

        if (validProjects.isEmpty()) {
            return;
        }

        Map<ProjectId, ProjectTracking> trackingMap = new HashMap<>();
        for (Project p : validProjects) {
            ProjectTracking t = projectTrackingRepository.findByProjectId(p.getId())
                    .orElseThrow(() -> new ProjectException(
                            "Tracking missing for project: " + p.getId().value(),
                            ProjectErrorCode.PROJECT_NOT_FOUND));
            trackingMap.put(p.getId(), t);
        }

        LocalDate today = LocalDate.now();
        LocalDate firstOfPrevMonth = today.minusMonths(1).withDayOfMonth(1);
        LocalDate firstOfThisMonth = today.withDayOfMonth(1);

        LocalDateTime startOfPrevMonth = firstOfPrevMonth.atStartOfDay();
        LocalDateTime endOfPrevMonth   = firstOfThisMonth.atStartOfDay().minusNanos(1);

        // Actual Income
        List<Transaction> incomes = transactionRepository.findByUserIdWithFilters(
                ownerUuid, null, null, startOfPrevMonth, endOfPrevMonth, null, TransactionType.INCOME
        );
        BigDecimal actualIncome = incomes.stream()
                .map(t -> t.getAmount().getValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        if (actualIncome.compareTo(BigDecimal.ZERO) == 0) {
            actualIncome = user.getIncome() != null ? user.getIncome() : BigDecimal.ZERO;
        }

        // Actual Expenses
        List<Transaction> expenses = transactionRepository.findByUserIdWithFilters(
                ownerUuid, null, null, startOfPrevMonth, endOfPrevMonth, null, TransactionType.EXPENSE
        );
        BigDecimal actualExpenses = expenses.stream()
                .map(t -> t.getAmount().getValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal actualSavings = actualIncome.subtract(actualExpenses);

        // Sum of all monthly saving targets of active projects
        BigDecimal totalSavingGoals = validProjects.stream()
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE)
                .map(p -> trackingMap.get(p.getId()).getMonthlySaving())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netPenalty = BigDecimal.ZERO;
        BigDecimal netSurplus = BigDecimal.ZERO;

        if (actualSavings.compareTo(totalSavingGoals) >= 0) {
            netSurplus = actualSavings.subtract(totalSavingGoals).setScale(2, RoundingMode.HALF_UP);
        } else {
            netPenalty = totalSavingGoals.subtract(actualSavings).setScale(2, RoundingMode.HALF_UP);
        }

        Map<ProjectId, BigDecimal> finalMoneySaved = new HashMap<>();
        Map<ProjectId, BigDecimal> finalMoneyOwed = new HashMap<>();
        Map<ProjectId, BigDecimal> surplusInvestedMap = new HashMap<>();
        Map<ProjectId, BigDecimal> penaltyAppliedMap = new HashMap<>();

        for (Project p : validProjects) {
            ProjectTracking t = trackingMap.get(p.getId());
            BigDecimal newSaved = t.getMoneySaved();
            if (p.getStatus() == ProjectStatus.ACTIVE) {
                newSaved = newSaved.add(t.getMonthlySaving());
            }
            if (newSaved.compareTo(p.getTargetAmount().amount()) > 0) {
                newSaved = p.getTargetAmount().amount();
            }
            finalMoneySaved.put(p.getId(), newSaved);
            finalMoneyOwed.put(p.getId(), t.getMoneyOwed() != null ? t.getMoneyOwed() : BigDecimal.ZERO);
            surplusInvestedMap.put(p.getId(), BigDecimal.ZERO);
            penaltyAppliedMap.put(p.getId(), BigDecimal.ZERO);
        }

        if (netSurplus.compareTo(BigDecimal.ZERO) > 0) {
            // 1. Repay Debt First (Highest to Lowest priority)
            List<Project> debtOrder = validProjects.stream()
                    .sorted(Comparator.comparingInt(p -> -p.getPriority().ordinal())) // HIGH -> LOW
                    .toList();

            for (Project p : debtOrder) {
                if (netSurplus.compareTo(BigDecimal.ZERO) <= 0) break;
                BigDecimal currentOwed = finalMoneyOwed.get(p.getId());
                if (currentOwed.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal toPay = netSurplus.min(currentOwed);
                    finalMoneyOwed.put(p.getId(), currentOwed.subtract(toPay));
                    netSurplus = netSurplus.subtract(toPay);
                    log.info("[Settlement] Project {} debt reduced by {}.", p.getId().value(), toPay);
                }
            }

            // 2. Extra Savings (AutoInvest surplus, HIGH -> LOW)
            List<Project> surplusOrder = validProjects.stream()
                    .sorted(Comparator.comparingInt(p -> -p.getPriority().ordinal())) // HIGH -> LOW
                    .toList();

            for (Project p : surplusOrder) {
                if (netSurplus.compareTo(BigDecimal.ZERO) <= 0) break;

                BigDecimal currentSaved = finalMoneySaved.get(p.getId());
                BigDecimal target = p.getTargetAmount().amount();
                BigDecimal room = target.subtract(currentSaved);

                if (room.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal toAdd = netSurplus.min(room);
                    finalMoneySaved.put(p.getId(), currentSaved.add(toAdd));
                    surplusInvestedMap.put(p.getId(), toAdd);
                    netSurplus = netSurplus.subtract(toAdd);
                }
            }
        } else if (netPenalty.compareTo(BigDecimal.ZERO) > 0) {
            // Distribute Penalty (LOW -> HIGH priority)
            List<Project> penaltyOrder = validProjects.stream()
                    .filter(p -> p.getStatus() == ProjectStatus.ACTIVE)
                    .sorted(Comparator.comparingInt(p -> p.getPriority().ordinal())) // LOW -> HIGH
                    .toList();

            for (int i = 0; i < penaltyOrder.size(); i++) {
                Project p = penaltyOrder.get(i);
                if (netPenalty.compareTo(BigDecimal.ZERO) <= 0) break;

                ProjectTracking t = trackingMap.get(p.getId());
                BigDecimal monthlySaving = t.getMonthlySaving() != null ? t.getMonthlySaving() : BigDecimal.ZERO;

                BigDecimal toAdd;
                if (i == penaltyOrder.size() - 1) {
                    toAdd = netPenalty;
                } else {
                    toAdd = netPenalty.min(monthlySaving);
                }

                BigDecimal currentOwed = finalMoneyOwed.get(p.getId());
                finalMoneyOwed.put(p.getId(), currentOwed.add(toAdd));
                penaltyAppliedMap.put(p.getId(), toAdd);
                netPenalty = netPenalty.subtract(toAdd);
            }
        }

        int freezeMult = 3;
        int maxFrozenMonths = 3;

        for (Project p : validProjects) {
            ProjectTracking t = trackingMap.get(p.getId());
            BigDecimal newSaved = finalMoneySaved.get(p.getId());
            BigDecimal newOwed = finalMoneyOwed.get(p.getId());

            int newMonthLeft = SettlementEngine.recalculateMonthLeft(
                    p.getTargetAmount().amount(), newSaved, t.getMonthlySaving(), t.getMonthLeft());

            BigDecimal moneySavedBefore = t.getMoneySaved();
            int monthLeftBefore = t.getMonthLeft();

            int newFrozenMonths = t.getFrozenMonths();

            // Freeze Lifecycle
            if (p.getStatus() == ProjectStatus.ACTIVE) {
                BigDecimal freezeLimit = t.getMonthlySaving().multiply(BigDecimal.valueOf(freezeMult));
                if (newOwed.compareTo(freezeLimit) >= 0) {
                    p.updateStatus(ownerId, ProjectStatus.FROZEN);
                    newFrozenMonths = 0; // reset counter
                    log.info("[Settlement] Project {} frozen due to high debt: {}.", p.getId().value(), newOwed);
                }
            } else if (p.getStatus() == ProjectStatus.FROZEN) {
                if (newOwed.compareTo(BigDecimal.ZERO) == 0) {
                    p.updateStatus(ownerId, ProjectStatus.ACTIVE);
                    newFrozenMonths = 0;
                    log.info("[Settlement] Project {} un-frozen as debt cleared.", p.getId().value());
                } else {
                    newFrozenMonths++;
                    if (newFrozenMonths >= maxFrozenMonths) {
                        p.updateStatus(ownerId, ProjectStatus.EXPIRED);
                        log.info("[Settlement] Project {} expired because it remained frozen for {} month(s).", p.getId().value(), newFrozenMonths);
                    }
                }
            }

            t.update(newSaved, t.getCurrentMonth() + 1, newMonthLeft, newOwed);
            t.setFrozenMonths(newFrozenMonths);
            projectTrackingRepository.save(t);

            ProjectHistory snapshot = ProjectHistory.createSnapshot(
                    p.getId(), ownerUuid, firstOfPrevMonth.getMonthValue(),
                    firstOfPrevMonth.getYear(), moneySavedBefore, newSaved, t.getMonthlySaving(),
                    penaltyAppliedMap.get(p.getId()), surplusInvestedMap.get(p.getId()), monthLeftBefore, newMonthLeft
            );
            projectHistoryRepository.save(snapshot);

            if (newMonthLeft <= 0 || newSaved.compareTo(p.getTargetAmount().amount()) >= 0) {
                p.updateStatus(ownerId, ProjectStatus.COMPLETED);
                projectRepository.save(p);
                log.info("[Settlement] Project {} marked COMPLETED.", p.getId().value());
            } else {
                projectRepository.save(p);
            }

            log.info("[Settlement] Settled project {} | saved: {} → {} | owed: {} → {} | monthLeft: {} → {}",
                    p.getId().value(), moneySavedBefore, newSaved, t.getMoneyOwed(), newOwed, monthLeftBefore, newMonthLeft);
        }

        safeSpendingService.recalculateAndSaveSafeSpending(ownerId);
    }
}
