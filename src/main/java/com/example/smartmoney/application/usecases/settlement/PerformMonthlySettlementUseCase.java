package com.example.smartmoney.application.usecases.settlement;

import com.example.smartmoney.application.usecases.notification.CreateNotificationUseCase;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectHistory;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Focus_Mode;
import com.example.smartmoney.domain.models.user.enums.Intervention_Level;
import com.example.smartmoney.domain.repositories.ProjectHistoryRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectTrackingRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
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
    private final CreateNotificationUseCase createNotificationUseCase;

    /** Lifecycle transitions worth telling the user about. Severity ordering drives notification gating. */
    private enum SettlementEvent { COMPLETED, FROZEN, EXPIRED }

    /**
     * Synthetic ledger categories that mirror project money movement (a press-to-contribute EXPENSE
     * and an abandonment-refund INCOME). They are internal bookkeeping, not real earning/spending, so
     * settlement's income/expense sums must skip them. See docs/settlement-insights-followup.md 0b.
     */
    private static final Set<Category> SYNTHETIC_PROJECT_CATEGORIES =
            EnumSet.of(Category.PROJECT_CONTRIBUTION, Category.PROJECT_REFUND);

    private static boolean isSyntheticProjectLedgerEntry(Transaction t) {
        return SYNTHETIC_PROJECT_CATEGORIES.contains(t.getCategory());
    }

    public void execute() {
        // Settle both ACTIVE and FROZEN projects: a frozen project must still be re-evaluated each
        // month so it can auto-unfreeze once its debt clears or expire after too long frozen — loading
        // only ACTIVE would strand every frozen project forever (nothing to un-freeze or expire it).
        List<Project> projects = new ArrayList<>();
        projects.addAll(projectRepository.findAllByStatus(ProjectStatus.ACTIVE));
        projects.addAll(projectRepository.findAllByStatus(ProjectStatus.FROZEN));
        log.info("[Settlement] Starting monthly settlement for {} project(s).", projects.size());

        Map<UserId, List<Project>> userProjects = projects.stream()
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

        // Focus_Mode.TRACK_ONLY users opt out of debt/penalties/freeze: savings still credit and
        // projects can still complete, but no moneyOwed accrues and the freeze/expire lifecycle is
        // skipped. See docs/settlement-revival-plan.md Step 2.
        boolean penaltiesEnabled = user.getFocusMode() != Focus_Mode.TRACK_ONLY;

        // Re-fetch each project as a full aggregate (contributions + tracking). findAllByStatus loads
        // no contributions, and ProjectRepository.save() treats the in-memory contribution list as
        // authoritative — settling a stub-loaded project would delete its entire contribution history.
        // Progress and completion read sum(contributions), so settlement must operate on that list too.
        // See docs/settlement-insights-followup.md item 0.
        List<Project> validProjects = new ArrayList<>();
        for (Project stub : projects) {
            if (stub.getStatus() != ProjectStatus.ACTIVE && stub.getStatus() != ProjectStatus.FROZEN) {
                continue;
            }
            Project full = projectRepository.findById(stub.getId())
                    .orElseThrow(() -> new ProjectException(
                            "Project not found: " + stub.getId().value(),
                            ProjectErrorCode.PROJECT_NOT_FOUND));
            validProjects.add(full);
        }

        if (validProjects.isEmpty()) {
            return;
        }

        Map<ProjectId, ProjectTracking> trackingMap = new HashMap<>();
        for (Project p : validProjects) {
            ProjectTracking t = p.getTracking();
            if (t == null) {
                throw new ProjectException(
                        "Tracking missing for project: " + p.getId().value(),
                        ProjectErrorCode.PROJECT_NOT_FOUND);
            }
            trackingMap.put(p.getId(), t);
        }

        LocalDate today = LocalDate.now();
        LocalDate firstOfPrevMonth = today.minusMonths(1).withDayOfMonth(1);
        LocalDate firstOfThisMonth = today.withDayOfMonth(1);

        LocalDateTime startOfPrevMonth = firstOfPrevMonth.atStartOfDay();
        LocalDateTime endOfPrevMonth   = firstOfThisMonth.atStartOfDay().minusNanos(1);

        // Actual Income. Excludes PROJECT_REFUND (a synthetic INCOME mirror emitted on project
        // abandonment) so a refund is never counted as fresh earnings. See followup 0b.
        List<Transaction> incomes = transactionRepository.findByUserIdWithFilters(
                ownerUuid, null, null, startOfPrevMonth, endOfPrevMonth, null, TransactionType.INCOME
        );
        BigDecimal actualIncome = incomes.stream()
                .filter(t -> !isSyntheticProjectLedgerEntry(t))
                .map(t -> t.getAmount().getValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        if (actualIncome.compareTo(BigDecimal.ZERO) == 0) {
            actualIncome = user.getIncome() != null ? user.getIncome() : BigDecimal.ZERO;
        }

        // Actual Expenses. Excludes PROJECT_CONTRIBUTION (a synthetic EXPENSE mirror of a
        // press-to-contribute) so saving toward a goal is never mistaken for overspending and
        // penalised — settlement would otherwise accrue debt for the very act of saving. Followup 0b.
        List<Transaction> expenses = transactionRepository.findByUserIdWithFilters(
                ownerUuid, null, null, startOfPrevMonth, endOfPrevMonth, null, TransactionType.EXPENSE
        );
        BigDecimal actualExpenses = expenses.stream()
                .filter(t -> !isSyntheticProjectLedgerEntry(t))
                .map(t -> t.getAmount().getValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal actualSavings = actualIncome.subtract(actualExpenses);

        // Sum of all monthly saving targets of active projects
        BigDecimal totalSavingGoals = validProjects.stream()
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE)
                .map(p -> trackingMap.get(p.getId()).getMonthlySaving())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Saving_Pace caps the expected saving: never penalise a user for failing to save beyond
        // their means (income × pace rate). Per-project distribution still uses each project's own
        // monthlySaving unchanged. See docs/settlement-revival-plan.md Step 1.
        BigDecimal capacity = user.getSavingCapacity();
        BigDecimal effectiveGoals = SettlementEngine.effectiveGoals(totalSavingGoals, capacity);

        BigDecimal netPenalty = BigDecimal.ZERO;
        BigDecimal netSurplus = BigDecimal.ZERO;

        if (actualSavings.compareTo(effectiveGoals) >= 0) {
            netSurplus = actualSavings.subtract(effectiveGoals).setScale(2, RoundingMode.HALF_UP);
        } else if (penaltiesEnabled) {
            netPenalty = effectiveGoals.subtract(actualSavings).setScale(2, RoundingMode.HALF_UP);
        }

        // Progress is sum(contributions). savedBefore = that total pre-settlement; finalMoneySaved =
        // the total after this month's credit; creditMap = the amount to append as an auto-contribution.
        Map<ProjectId, BigDecimal> savedBefore = new HashMap<>();
        Map<ProjectId, BigDecimal> finalMoneySaved = new HashMap<>();
        Map<ProjectId, BigDecimal> finalMoneyOwed = new HashMap<>();
        Map<ProjectId, BigDecimal> creditMap = new HashMap<>();
        Map<ProjectId, BigDecimal> surplusInvestedMap = new HashMap<>();
        Map<ProjectId, BigDecimal> penaltyAppliedMap = new HashMap<>();

        for (Project p : validProjects) {
            ProjectTracking t = trackingMap.get(p.getId());
            BigDecimal currentSaved = p.totalContributed().amount();
            BigDecimal target = p.getTargetAmount().amount();

            // Only ACTIVE projects auto-save the monthly amount; frozen projects are paused. Capped so
            // the credited contribution never overshoots the target.
            BigDecimal baseCredit = BigDecimal.ZERO;
            if (p.getStatus() == ProjectStatus.ACTIVE) {
                BigDecimal room = target.subtract(currentSaved).max(BigDecimal.ZERO);
                baseCredit = t.getMonthlySaving().min(room);
            }

            savedBefore.put(p.getId(), currentSaved);
            finalMoneySaved.put(p.getId(), currentSaved.add(baseCredit));
            creditMap.put(p.getId(), baseCredit);
            finalMoneyOwed.put(p.getId(), t.getMoneyOwed() != null ? t.getMoneyOwed() : BigDecimal.ZERO);
            surplusInvestedMap.put(p.getId(), BigDecimal.ZERO);
            penaltyAppliedMap.put(p.getId(), BigDecimal.ZERO);
        }

        if (netSurplus.compareTo(BigDecimal.ZERO) > 0) {
            // 1. Repay Debt First (Highest to Lowest priority). Always runs — clearing existing
            //    arrears is correct even for TRACK_ONLY users and when auto-invest is off.
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

            // 2. Extra Savings (auto-invest surplus, HIGH -> LOW). Gated on the user's
            //    autoInvestSurplus preference: when off, leftover surplus stays as free cash.
            //    Only ACTIVE projects — surplus is credited as a contribution, and a paused (frozen)
            //    project cannot receive one.
            if (user.isAutoInvestSurplus()) {
                List<Project> surplusOrder = validProjects.stream()
                        .filter(p -> p.getStatus() == ProjectStatus.ACTIVE)
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
                        creditMap.merge(p.getId(), toAdd, BigDecimal::add);
                        surplusInvestedMap.put(p.getId(), toAdd);
                        netSurplus = netSurplus.subtract(toAdd);
                    }
                }
            }
        } else if (netPenalty.compareTo(BigDecimal.ZERO) > 0) {
            // Distribute Penalty (LOW -> HIGH priority). Unreachable when penalties are disabled
            // (netPenalty stays zero for TRACK_ONLY users).
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

        // Shared with the chatbot's settlement forecast block, which tells the user how far their
        // debt is from freezing — the two must never quote different thresholds.
        int maxFrozenMonths = SettlementEngine.MAX_FROZEN_MONTHS;

        for (Project p : validProjects) {
            ProjectTracking t = trackingMap.get(p.getId());
            BigDecimal newSaved = finalMoneySaved.get(p.getId());
            BigDecimal newOwed = finalMoneyOwed.get(p.getId());
            BigDecimal credit = creditMap.get(p.getId());

            // Credit this month's auto-saving as a ProjectContribution — the one source of progress —
            // BEFORE any freeze transition, since contributions require an ACTIVE project. Only ACTIVE
            // projects carry a positive credit, so this never fires against a paused project.
            if (credit != null && credit.compareTo(BigDecimal.ZERO) > 0) {
                p.recordAutoSaving(credit, "Auto-saved this month");
            }

            int newMonthLeft = SettlementEngine.recalculateMonthLeft(
                    p.getTargetAmount().amount(), newSaved, t.getMonthlySaving(), t.getMonthLeft());

            BigDecimal moneySavedBefore = savedBefore.get(p.getId());
            int monthLeftBefore = t.getMonthLeft();

            int newFrozenMonths = t.getFrozenMonths();
            boolean frozenThisMonth = false;

            // Freeze Lifecycle. The punitive transitions (freeze on high debt, expire on stuck-frozen)
            // are gated on penaltiesEnabled so TRACK_ONLY users never accrue them. Unfreezing a
            // debt-free project, however, always applies: it is never punitive, and skipping it for
            // TRACK_ONLY would strand a project FROZEN with zero debt (see the surplus repay phase).
            if (p.getStatus() == ProjectStatus.ACTIVE) {
                if (penaltiesEnabled) {
                    BigDecimal freezeLimit = SettlementEngine.freezeThreshold(t.getMonthlySaving());
                    if (newOwed.compareTo(freezeLimit) >= 0) {
                        p.updateStatus(ownerId, ProjectStatus.FROZEN);
                        newFrozenMonths = 0; // reset counter
                        frozenThisMonth = true;
                        log.info("[Settlement] Project {} frozen due to high debt: {}.", p.getId().value(), newOwed);
                        notifyIfEligible(user, p, SettlementEvent.FROZEN);
                    }
                }
            } else if (p.getStatus() == ProjectStatus.FROZEN) {
                if (newOwed.compareTo(BigDecimal.ZERO) == 0) {
                    p.updateStatus(ownerId, ProjectStatus.ACTIVE);
                    newFrozenMonths = 0;
                    log.info("[Settlement] Project {} un-frozen as debt cleared.", p.getId().value());
                } else if (penaltiesEnabled) {
                    newFrozenMonths++;
                    if (newFrozenMonths >= maxFrozenMonths) {
                        p.updateStatus(ownerId, ProjectStatus.EXPIRED);
                        log.info("[Settlement] Project {} expired because it remained frozen for {} month(s).", p.getId().value(), newFrozenMonths);
                        notifyIfEligible(user, p, SettlementEvent.EXPIRED);
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

            // Terminal transitions (skip a project already frozen or expired this run — it still
            // carries debt and a contradictory pair of notifications would fire; it can settle in a
            // later run once the debt clears):
            //   - reached target  → COMPLETED;
            //   - saving horizon elapsed (monthLeft ≤ 0) still under target → EXPIRED, NOT completed.
            // The horizon-elapsed case is a deadline failure independent of debt, so it applies to
            // every focus mode (mirrors the lazy deadline expiry elsewhere).
            boolean funded = newSaved.compareTo(p.getTargetAmount().amount()) >= 0;
            if (!frozenThisMonth && p.getStatus() != ProjectStatus.EXPIRED && funded) {
                p.updateStatus(ownerId, ProjectStatus.COMPLETED);
                projectRepository.save(p);
                log.info("[Settlement] Project {} marked COMPLETED.", p.getId().value());
                notifyIfEligible(user, p, SettlementEvent.COMPLETED);
            } else if (!frozenThisMonth && p.getStatus() != ProjectStatus.EXPIRED && newMonthLeft <= 0) {
                p.updateStatus(ownerId, ProjectStatus.EXPIRED);
                projectRepository.save(p);
                log.info("[Settlement] Project {} expired — saving horizon elapsed under target ({} of {}).",
                        p.getId().value(), newSaved, p.getTargetAmount().amount());
                notifyIfEligible(user, p, SettlementEvent.EXPIRED);
            } else {
                projectRepository.save(p);
            }

            log.info("[Settlement] Settled project {} | saved: {} → {} | owed: {} → {} | monthLeft: {} → {}",
                    p.getId().value(), moneySavedBefore, newSaved, t.getMoneyOwed(), newOwed, monthLeftBefore, newMonthLeft);
        }
    }

    /**
     * Push a settlement notification if the user's Intervention_Level clears the event's severity
     * threshold. The debt/freeze math is identical for every level — only notification loudness
     * differs. See docs/settlement-revival-plan.md Step 3.
     */
    private void notifyIfEligible(User user, Project project, SettlementEvent event) {
        Intervention_Level level = user.getInterventionLevel();
        boolean push = switch (event) {
            case COMPLETED -> level == Intervention_Level.HARD;                                  // INFO
            case FROZEN    -> level == Intervention_Level.GENTLE || level == Intervention_Level.HARD; // WARN
            case EXPIRED   -> true;                                                               // CRITICAL
        };
        if (!push) {
            return;
        }

        // Pipe-delimited i18n key; the FE localizes it (see NotificationListModal.tsx).
        String name = project.getName();
        String content = switch (event) {
            case COMPLETED -> "notification.settlement.project_completed|" + name;
            case FROZEN    -> "notification.settlement.project_frozen|" + name;
            case EXPIRED   -> "notification.settlement.project_expired|" + name;
        };
        String deepLink = "app://projects/" + project.getId().value();
        createNotificationUseCase.createNotification(user.getId(), content, deepLink);
    }
}
