package com.example.smartmoney.application.usecases.settlement;

import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectHistory;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.user.UserIncome;
import com.example.smartmoney.domain.repositories.ProjectHistoryRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectTrackingRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.repositories.UserIncomeRepository;
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
    private final UserIncomeRepository userIncomeRepository;
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

        Optional<UserIncome> incomeOpt = userIncomeRepository.findByUserId(ownerUuid);
        if (incomeOpt.isEmpty()) {
            log.warn("[Settlement] No UserIncome found for owner {}. Skipping.", ownerUuid);
            return;
        }
        UserIncome userIncome = incomeOpt.get();

        Map<ProjectId, ProjectTracking> trackingMap = new HashMap<>();
        for (Project p : projects) {
            projectTrackingRepository.findByProjectId(p.getId())
                    .ifPresent(t -> trackingMap.put(p.getId(), t));
        }

        List<Project> validProjects = projects.stream()
                .filter(p -> trackingMap.containsKey(p.getId()))
                .toList();

        if (validProjects.isEmpty()) return;

        LocalDate today = LocalDate.now();
        LocalDate firstOfPrevMonth = today.minusMonths(1).withDayOfMonth(1);
        LocalDate firstOfThisMonth = today.withDayOfMonth(1);

        LocalDateTime startOfPrevMonth = firstOfPrevMonth.atStartOfDay();
        LocalDateTime endOfPrevMonth   = firstOfThisMonth.atStartOfDay().minusNanos(1);

        List<Transaction> expenses = transactionRepository.findByUserIdWithFilters(
                ownerUuid, null, null, startOfPrevMonth, endOfPrevMonth, null, TransactionType.EXPENSE
        );

        BigDecimal actualExpenses = expenses.stream()
                .map(t -> t.getAmount().getValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // SafeSpending as stored is already penalized. 
        // We need the "Base Safe Spending" (BSS) = UsableIncome - Sum(Monthly Savings)
        // ESS (Effective Safe Spending) = BSS - TotalDebt
        BigDecimal totalDebt = validProjects.stream()
                .map(p -> {
                    ProjectTracking t = trackingMap.get(p.getId());
                    return t.getMoneyOwed() != null ? t.getMoneyOwed() : BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal currentSafeSpending = userIncome.getSafeSpending() != null
                ? BigDecimal.valueOf(userIncome.getSafeSpending())
                : BigDecimal.ZERO;

        BigDecimal baseSafeSpending = currentSafeSpending.add(totalDebt);

        BigDecimal netPenalty = BigDecimal.ZERO;
        BigDecimal netSurplus = BigDecimal.ZERO;

        if (actualExpenses.compareTo(baseSafeSpending) > 0) {
            netPenalty = actualExpenses.subtract(baseSafeSpending).setScale(2, RoundingMode.HALF_UP);
        } else {
            netSurplus = baseSafeSpending.subtract(actualExpenses).setScale(2, RoundingMode.HALF_UP);
        }

        Map<ProjectId, BigDecimal> finalMoneySaved = new HashMap<>();
        Map<ProjectId, BigDecimal> finalMoneyOwed = new HashMap<>();
        Map<ProjectId, BigDecimal> surplusInvestedMap = new HashMap<>();
        Map<ProjectId, BigDecimal> penaltyAppliedMap = new HashMap<>();

        for (Project p : validProjects) {
            ProjectTracking t = trackingMap.get(p.getId());
            BigDecimal newSaved = t.getMoneySaved().add(t.getMonthlySaving());
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

            // 2. Extra Savings (If remaining surplus and AutoInvest is true)
            if (netSurplus.compareTo(BigDecimal.ZERO) > 0 && Boolean.TRUE.equals(userIncome.getAutoInvestSurplus())) {
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
            }
        } else if (netPenalty.compareTo(BigDecimal.ZERO) > 0) {
            // Distribute Penalty
            List<Project> penaltyOrder = validProjects.stream()
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

        for (Project p : validProjects) {
            ProjectTracking t = trackingMap.get(p.getId());
            BigDecimal newSaved = finalMoneySaved.get(p.getId());
            BigDecimal newOwed = finalMoneyOwed.get(p.getId());

            int newMonthLeft = SettlementEngine.recalculateMonthLeft(
                    p.getTargetAmount().amount(), newSaved, t.getMonthlySaving(), t.getMonthLeft());

            BigDecimal moneySavedBefore = t.getMoneySaved();
            int monthLeftBefore = t.getMonthLeft();

            t.update(newSaved, t.getCurrentMonth() + 1, newMonthLeft, newOwed);
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
            }

            log.info("[Settlement] Settled project {} | saved: {} → {} | owed: {} → {} | monthLeft: {} → {}",
                    p.getId().value(), t.getMoneySaved(), newSaved, t.getMoneyOwed(), newOwed, t.getMonthLeft(), newMonthLeft);
        }

        // This call will recalculate Safe Spending based on the NEW total debt
        safeSpendingService.recalculateAndSaveSafeSpending(ownerId);
    }
}
