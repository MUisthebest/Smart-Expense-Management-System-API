package com.example.smartmoney.application.usecases.settlement;

import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectHistory;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.user.UserIncome;
import com.example.smartmoney.domain.repositories.ProjectHistoryRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectTrackingRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.repositories.UserIncomeRepository;
import com.example.smartmoney.domain.services.SettlementEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application use case for performing the monthly financial settlement.
 *
 * <p>Called once per month (on the 1st) by {@code MonthlySettlementScheduler}.
 * For each ACTIVE project it:
 * <ol>
 *   <li>Sums previous-month EXPENSE transactions for the project owner.</li>
 *   <li>Computes a penalty for overspending.</li>
 *   <li>Adds the monthly saving (+ optional surplus) to money_saved.</li>
 *   <li>Recalculates months_left using {@link SettlementEngine}.</li>
 *   <li>Penalises next month's safe_spending.</li>
 *   <li>Persists {@code ProjectTracking}, {@code UserIncome}, and a {@code ProjectHistory} snapshot.</li>
 *   <li>Marks the project COMPLETED when months_left reaches 0.</li>
 * </ol>
 * Each project is processed in its own {@code @Transactional} boundary so that
 * a failure on one project does not roll back the others.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerformMonthlySettlementUseCase {

    private final ProjectRepository projectRepository;
    private final ProjectTrackingRepository projectTrackingRepository;
    private final TransactionRepository transactionRepository;
    private final UserIncomeRepository userIncomeRepository;
    private final ProjectHistoryRepository projectHistoryRepository;

    public void execute() {
        List<Project> activeProjects = projectRepository.findAllByStatus(ProjectStatus.ACTIVE);
        log.info("[Settlement] Starting monthly settlement for {} active project(s).", activeProjects.size());

        for (Project project : activeProjects) {
            try {
                settleProject(project);
            } catch (Exception e) {
                log.error("[Settlement] Failed to settle project {} (owner={}): {}",
                        project.getId().value(), project.getOwnerId().value(), e.getMessage(), e);
            }
        }

        log.info("[Settlement] Monthly settlement complete.");
    }

    /**
     * Wraps each project's settlement in its own transaction so failures
     * on one project do not affect others.
     */
    @Transactional
    public void settleProject(Project project) {
        UUID projectUuid = project.getId().value();
        UUID ownerUuid = project.getOwnerId().value();

        // ── 1. Load tracking ──────────────────────────────────────────────────
        Optional<ProjectTracking> trackingOpt =
                projectTrackingRepository.findByProjectId(project.getId());
        if (trackingOpt.isEmpty()) {
            log.warn("[Settlement] No tracking found for project {}. Skipping.", projectUuid);
            return;
        }
        ProjectTracking tracking = trackingOpt.get();

        // ── 2. Load user income ───────────────────────────────────────────────
        Optional<UserIncome> incomeOpt = userIncomeRepository.findById(ownerUuid);
        if (incomeOpt.isEmpty()) {
            log.warn("[Settlement] No UserIncome found for owner {}. Skipping project {}.",
                    ownerUuid, projectUuid);
            return;
        }
        UserIncome userIncome = incomeOpt.get();

        // ── 3. Determine previous calendar month window ───────────────────────
        LocalDate today = LocalDate.now();
        LocalDate firstOfPrevMonth = today.minusMonths(1).withDayOfMonth(1);
        LocalDate firstOfThisMonth = today.withDayOfMonth(1);

        LocalDateTime startOfPrevMonth = firstOfPrevMonth.atStartOfDay();
        LocalDateTime endOfPrevMonth   = firstOfThisMonth.atStartOfDay().minusNanos(1);

        // ── 4. Aggregate EXPENSE transactions for the previous month ──────────
        List<Transaction> expenses = transactionRepository.findByUserIdWithFilters(
                ownerUuid,
                null, null,
                startOfPrevMonth, endOfPrevMonth,
                null,
                TransactionType.EXPENSE
        );

        BigDecimal actualExpenses = expenses.stream()
                .map(t -> t.getAmount().getValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // ── 5. Safe spending as BigDecimal (store is Integer) ─────────────────
        BigDecimal safeSpendingBD = userIncome.getSafeSpending() != null
                ? BigDecimal.valueOf(userIncome.getSafeSpending())
                : BigDecimal.ZERO;

        // Keep the original value BEFORE any mutation for penalty deduction
        BigDecimal originalSafeSpending = safeSpendingBD;

        // ── 6. Penalty ────────────────────────────────────────────────────────
        BigDecimal penalty = SettlementEngine.calculatePenalty(actualExpenses, safeSpendingBD);

        // ── 7. Update savings (base + optional surplus) ───────────────────────
        BigDecimal moneySavedBefore = tracking.getMoneySaved();
        BigDecimal monthlySaving    = tracking.getMonthlySaving();

        BigDecimal newSaved = moneySavedBefore.add(monthlySaving);

        BigDecimal surplusInvested = BigDecimal.ZERO;
        if (actualExpenses.compareTo(safeSpendingBD) < 0 && Boolean.TRUE.equals(userIncome.getAutoInvestSurplus())) {
            surplusInvested = safeSpendingBD.subtract(actualExpenses).setScale(2, RoundingMode.HALF_UP);
            newSaved = newSaved.add(surplusInvested);
        }

        // Cap saved at target so we never overshoot
        BigDecimal targetAmount = project.getTargetAmount().amount();
        if (newSaved.compareTo(targetAmount) > 0) {
            newSaved = targetAmount;
        }
        newSaved = newSaved.setScale(2, RoundingMode.HALF_UP);

        // ── 8. Recalculate months left ────────────────────────────────────────
        int monthLeftBefore = tracking.getMonthLeft();
        int newMonthLeft = SettlementEngine.recalculateMonthLeft(
                targetAmount, newSaved, monthlySaving, monthLeftBefore);

        // ── 9. Persist ProjectTracking ────────────────────────────────────────
        tracking.update(
                newSaved,
                tracking.getCurrentMonth() + 1,
                newMonthLeft,
                tracking.getMoneyOwed()
        );
        projectTrackingRepository.save(tracking);

        // ── 10. Penalise next month's safe spending ───────────────────────────
        BigDecimal penalisedSafeSpending = originalSafeSpending.subtract(penalty);
        // Floor at zero — safe spending cannot be negative
        if (penalisedSafeSpending.compareTo(BigDecimal.ZERO) < 0) {
            penalisedSafeSpending = BigDecimal.ZERO;
        }
        userIncome.setSafeSpending(penalisedSafeSpending.setScale(0, RoundingMode.HALF_UP).intValue());
        userIncomeRepository.save(userIncome);

        // ── 11. Write history snapshot ────────────────────────────────────────
        ProjectHistory snapshot = ProjectHistory.createSnapshot(
                project.getId(),
                ownerUuid,
                firstOfPrevMonth.getMonthValue(),
                firstOfPrevMonth.getYear(),
                moneySavedBefore,
                newSaved,
                monthlySaving,
                penalty,
                surplusInvested,
                monthLeftBefore,
                newMonthLeft
        );
        projectHistoryRepository.save(snapshot);

        // ── 12. Mark project COMPLETED if finished ────────────────────────────
        if (newMonthLeft == 0) {
            project.updateStatus(project.getOwnerId(), ProjectStatus.COMPLETED);
            projectRepository.save(project);
            log.info("[Settlement] Project {} marked COMPLETED.", projectUuid);
        }

        log.info("[Settlement] Settled project {} | saved: {} → {} | penalty: {} | monthLeft: {} → {}",
                projectUuid, moneySavedBefore, newSaved, penalty, monthLeftBefore, newMonthLeft);
    }
}
