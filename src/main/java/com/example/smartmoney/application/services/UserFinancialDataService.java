package com.example.smartmoney.application.services;


import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.GroupProject;
import com.example.smartmoney.domain.models.group.enums.GroupStatus;
import com.example.smartmoney.domain.models.insight.Insight;
import com.example.smartmoney.domain.models.insight.Severity;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Focus_Mode;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.GroupRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.application.usecases.settlement.SettlementProperties;
import com.example.smartmoney.domain.services.EnvelopeCalculator;
import com.example.smartmoney.domain.services.EnvelopeSnapshot;
import com.example.smartmoney.domain.services.SettlementEngine;
import com.example.smartmoney.domain.services.SpendingInsightAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserFinancialDataService {

    /** Cap on the group rows listed in the chat context, matching the projects section's cap —
     *  enough to name the group the user means without crowding out the financial blocks. */
    private static final int MAX_GROUPS_LISTED = 5;

    private final TransactionRepository transactionRepository;
    private final ProjectRepository projectRepository;
    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupProjectRepository groupProjectRepository;
    /** The codebase's single definition of "how much can this user spend" — used here so the
     *  number quoted in chat is bit-for-bit the number the budget-allocation endpoint computes,
     *  instead of a second inline {@code income − Σ monthlySaving} that drifts from it. */
    private final EnvelopeCalculator envelopeCalculator;
    /** Same deterministic analyzer behind {@code GET /api/v1/insights}. Its findings (budget pace
     *  projections, overspend anomalies, savings capacity, ...) are folded into the chat context
     *  so the assistant quotes the SAME analysis the Insights screen shows, rather than
     *  re-deriving a trend from the 15 transaction rows it can see. */
    private final SpendingInsightAnalyzer insightAnalyzer;
    /** Whether the month-end settlement cron is live. The forecast block describes what settlement
     *  WILL do on the 1st, so it must not promise a run that is switched off in this deployment. */
    private final SettlementProperties settlementProperties;

    public enum DataType {
        TRANSACTIONS, PROJECTS, INCOME, PROFILE
    }

    /**
     * Lấy dữ liệu tài chính của người dùng theo các loại được yêu cầu.
     *
     * @param userId   ID của người dùng
     * @param dataTypes Các loại dữ liệu cần lấy (nếu rỗng thì không lấy gì)
     * @return Chuỗi mô tả dữ liệu, sẵn sàng để đưa vào prompt
     */
    public String getUserDataSummary(UUID userId, Set<DataType> dataTypes) {
        if (dataTypes == null || dataTypes.isEmpty()) {
            return "";
        }

        LocalDate now = LocalDate.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).atStartOfDay();
        LocalDateTime endOfMonth = now.withDayOfMonth(now.lengthOfMonth()).atTime(23, 59, 59);

        // Chuẩn bị dữ liệu theo yêu cầu
        List<Transaction> transactions = dataTypes.contains(DataType.TRANSACTIONS)
                ? transactionRepository.findByUserIdWithFilters(userId, null, null, startOfMonth, endOfMonth, null, null)
                : Collections.emptyList();

        List<Project> projects = dataTypes.contains(DataType.PROJECTS)
                ? projectRepository.findAllByUser(UserId.of(userId.toString()))
                : Collections.emptyList();

        // Disabled: user_income / user_financial_profile tables removed.
        // INCOME and PROFILE data types are no longer populated.
        /*
        UserIncome income = dataTypes.contains(DataType.INCOME)
                ? userIncomeRepository.findByUserId(userId).orElse(null)
                : null;

        UserFinancialProfile profile = dataTypes.contains(DataType.PROFILE)
                ? profileRepository.findByUserId(userId).orElse(null)
                : null;
        */

        // Xây dựng chuỗi kết quả
        StringBuilder sb = new StringBuilder();
        sb.append("Thông tin tài chính của người dùng:\n");

        // Disabled: user_income / user_financial_profile tables removed.
        /*
        if (profile != null) {
            sb.append("- Tình trạng sống: ").append(profile.getLivingStatus()).append("\n");
            sb.append("- Mức thu nhập: ").append(profile.getIncomeLevel()).append("\n");
            sb.append("- Phong cách chi tiêu: ").append(profile.getSpendingStyle()).append("\n");
            sb.append("- Tình trạng gia đình: ").append(profile.getFamilyStatus()).append("\n");
            sb.append("- Hình thức làm việc: ").append(profile.getWorkStyle()).append("\n");
        }

        if (income != null) {
            sb.append("- Thu nhập ròng: ").append(income.getNetIncome()).append(" ").append(income.getCurrency()).append("\n");
            sb.append("- Thu nhập khả dụng: ").append(income.getUsableIncome()).append(" ").append(income.getCurrency()).append("\n");
            sb.append("- Chi tiêu an toàn: ").append(income.getSafeSpending()).append(" ").append(income.getCurrency()).append("\n");
        }
        */

        // Profile income is stated alongside the recorded totals for the same reason buildChatContext
        // separates them: a suggestion built on "you earned 0 this month" when the salary simply
        // hasn't been entered is worse than no suggestion at all.
        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.getIncome() != null && user.getIncome().compareTo(BigDecimal.ZERO) > 0) {
            sb.append(String.format("- Thu nhập lý thuyết theo hồ sơ: %,.0f VND/tháng\n", user.getIncome()));
        }

        if (!transactions.isEmpty()) {
            double totalIncome = transactions.stream()
                    .filter(t -> t.getType() == TransactionType.INCOME)
                    .mapToDouble(t -> t.getAmount().getValue().doubleValue())
                    .sum();
            double totalExpense = transactions.stream()
                    .filter(t -> t.getType() == TransactionType.EXPENSE)
                    .mapToDouble(t -> t.getAmount().getValue().doubleValue())
                    .sum();
            sb.append(String.format("- Thu nhập THỰC TẾ đã ghi nhận tháng này: %,.0f VND\n", totalIncome));
            sb.append(String.format("- Chi tiêu thực tế tháng này: %,.0f VND\n", totalExpense));

            var categoryExpenses = transactions.stream()
                    .filter(t -> t.getType() == TransactionType.EXPENSE)
                    .filter(t -> t.getCategory() != null)
                    .collect(Collectors.groupingBy(
                            Transaction::getCategory,
                            Collectors.summingDouble(t -> t.getAmount().getValue().doubleValue())
                    ));
            if (categoryExpenses.isEmpty()) {
                sb.append("- Hiện chưa ghi nhận giao dịch chi tiêu phát sinh từ danh mục nào trong tháng này.\n");
            } else {
                sb.append("- Chi tiêu theo danh mục (top 5):\n");
                categoryExpenses.entrySet().stream()
                        .sorted(Map.Entry.<Category, Double>comparingByValue().reversed())
                        .limit(5)
                        .forEach(e -> sb.append(String.format("  * %s: %,.0f VND\n",
                                FinancialTermFormatter.category(e.getKey(), false), e.getValue())));
            }
        } else if (dataTypes.contains(DataType.TRANSACTIONS)) {
            sb.append("- Không có giao dịch trong tháng này.\n");
        }

        // Only genuinely running projects are described as running. The old version listed every
        // project the user had ever created under the heading "Các dự án đang hoạt động" — a
        // completed goal and a goal that failed six months ago both read as live work in progress,
        // and a past deadline produced a cheerful "Còn -4 tháng".
        LocalDate today = LocalDate.now();
        List<Project> runningProjects = projects.stream()
                .filter(p -> effectiveStatus(p, today) == ProjectStatus.ACTIVE)
                .toList();
        if (!runningProjects.isEmpty()) {
            sb.append("- Các dự án đang hoạt động:\n");
            runningProjects.stream().limit(5).forEach(p -> {
                        Money target = p.getTargetAmount();
                        Money contributed = p.totalContributed();
                        Money remaining = p.remaining();

                        BigDecimal progress = p.progressPercent();

                        long monthsLeft = Math.max(0,
                                ChronoUnit.MONTHS.between(today, p.getDeadline()));

                        sb.append("""
                    * %s
                    - Mục tiêu: %s
                    - Đã tiết kiệm: %s
                    - Còn thiếu: %s
                    - Tiến độ: %.1f%%
                    - Còn %d tháng
                    - Ưu tiên: %s
                    """
                                .formatted(
                                        p.getName(),
                                        target,
                                        contributed,
                                        remaining,
                                        progress,
                                        monthsLeft,
                                        p.getPriority()
                                ));
                    }
            );
        } else if (dataTypes.contains(DataType.PROJECTS)) {
            sb.append("- Không có dự án nào đang hoạt động.\n");
        }

        return sb.toString();
    }

    /**
     * Every numeric value behind {@link #buildChatContext}'s prompt text, exposed so callers can
     * run proactive checks (near-limit, feasibility, month-end leftover, surplus trend) without
     * re-deriving them — and, more importantly, so nobody re-derives them <em>differently</em>.
     *
     * <p><b>The two incomes are not interchangeable.</b> {@code monthlyIncome} is the figure the
     * user typed during financial setup: a plan, a standing assumption, the basis of every
     * forward-looking number here. {@code recordedIncome} is what actually landed in the ledger
     * this month. They routinely disagree — the salary hasn't been entered yet, a freelance month
     * came in short, a bonus arrived — and a reply that silently swaps one for the other either
     * congratulates a user on money they haven't received or tells them their income "dropped"
     * when they simply haven't logged it. Forward-looking statements ("your monthly safe spending
     * is X") use {@code monthlyIncome}; statements about how this month is actually going use
     * {@code recordedIncome}. {@link #incomeRecordedThisMonth()} distinguishes "earned nothing"
     * from "hasn't been entered yet".
     *
     * @param monthlyIncome        {@code User.income} — the planned/profile monthly income. Zero
     *                             when financial setup was never completed
     * @param safeSpending         {@code max(0, monthlyIncome − activeCommitment)}. Planned
     *                             monthly headroom; does NOT decrease as the user spends
     * @param daysLeft             calendar days remaining in the current month
     * @param recordedIncome       Σ INCOME transactions actually recorded this month
     * @param recordedExpense      Σ EXPENSE transactions this month, including project
     *                             contributions — the figure the cash-flow status is judged on
     * @param livingExpense        Σ EXPENSE transactions this month EXCLUDING
     *                             {@code PROJECT_CONTRIBUTION} (money moved into a saving goal is
     *                             not consumption). This is what safe spending is drawn down by
     * @param activeCommitment     Σ {@code calculateMonthlyCommitment()} over projects that still
     *                             hold a claim on this month — see
     *                             {@link #holdsMonthlyCommitment(Project, LocalDate)}
     * @param releasedCommitment   Σ the same figure over projects whose claim has been RELEASED
     *                             (expired / completed / abandoned / frozen). Reported so a reply
     *                             can explain why safe spending went up
     * @param savingCapacity       {@code monthlyIncome × pace rate} — what the user's saving pace
     *                             says they should be putting aside
     * @param remainingSafeSpending{@code max(0, safeSpending − livingExpense)} — the answer to
     *                             "can I still spend X this month", precomputed so no caller (and
     *                             no LLM) has to do the subtraction itself and get it wrong
     * @param overCommitted        {@code activeCommitment > monthlyIncome}
     * @param financialSetupDone   whether onboarding is complete; false means every planned figure
     *                             above is a fallback, not a real plan
     * @param safeSpendingIsFallback true when {@code safeSpending} could not be planned from
     *                             income and is standing in as "budget limits minus budget spent"
     */
    public record FinancialSnapshot(
            BigDecimal monthlyIncome,
            BigDecimal safeSpending,
            int daysLeft,
            BigDecimal recordedIncome,
            BigDecimal recordedExpense,
            BigDecimal livingExpense,
            BigDecimal activeCommitment,
            BigDecimal releasedCommitment,
            BigDecimal savingCapacity,
            BigDecimal remainingSafeSpending,
            boolean overCommitted,
            boolean financialSetupDone,
            boolean safeSpendingIsFallback) {

        /** True once at least one INCOME transaction exists for the month — the difference between
         *  "this user earned nothing" and "this user hasn't entered their salary yet". */
        public boolean incomeRecordedThisMonth() {
            return recordedIncome != null && recordedIncome.compareTo(BigDecimal.ZERO) > 0;
        }

        /** Recorded income minus the planned figure; negative means this month is running behind
         *  the plan (or the income simply hasn't been logged). Zero when setup isn't done. */
        public BigDecimal incomeVsPlan() {
            if (monthlyIncome == null || monthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
                return BigDecimal.ZERO;
            }
            return (recordedIncome == null ? BigDecimal.ZERO : recordedIncome).subtract(monthlyIncome);
        }
    }

    /** Actual-cash-flow classification for a month. Matches the SURPLUS/BALANCED/DEFICIT the
     *  chatbot prompt sees in the financial-context block, so callers that need to gate their
     *  own behaviour on "this month is in deficit" (proactive-suggestion suppression, aggressive-
     *  tone downshift, ...) share ONE definition with what the LLM was told. UNKNOWN when
     *  there's no recorded income transaction this month — treat as "don't overclaim either way". */
    public enum CashFlowStatus { SURPLUS, BALANCED, DEFICIT, UNKNOWN }

    /**
     * Classifies this month's actual cash-flow from recorded transactions only (independent of
     * user.income). The 10% band mirrors buildChatContext's inline formula so both stay in
     * lockstep — if either threshold changes, this method changes and buildChatContext follows.
     */
    public CashFlowStatus classifyCashFlow(UUID userId, int month, int year) {
        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
        LocalDateTime start = monthStart.atStartOfDay();
        LocalDateTime end = monthEnd.atTime(23, 59, 59);
        List<Transaction> txs = transactionRepository.findByUserIdWithFilters(
                userId, null, null, start, end, null, null);
        BigDecimal totalIncome = txs.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .map(t -> t.getAmount().getValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpense = txs.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .map(t -> t.getAmount().getValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return classifyCashFlowFromTotals(totalIncome, totalExpense);
    }

    /** Shared totals-to-status math, so buildChatContext (which already has the totals) and
     *  classifyCashFlow (which computes them from scratch) use the same thresholds. */
    private CashFlowStatus classifyCashFlowFromTotals(BigDecimal totalIncome, BigDecimal totalExpense) {
        if (totalIncome == null || totalIncome.compareTo(BigDecimal.ZERO) <= 0) return CashFlowStatus.UNKNOWN;
        BigDecimal delta = totalIncome.subtract(totalExpense == null ? BigDecimal.ZERO : totalExpense);
        BigDecimal band = totalIncome.multiply(BigDecimal.valueOf(0.10));
        if (delta.compareTo(band) > 0) return CashFlowStatus.SURPLUS;
        if (delta.compareTo(band.negate()) < 0) return CashFlowStatus.DEFICIT;
        return CashFlowStatus.BALANCED;
    }

    public FinancialSnapshot getFinancialSnapshot(UUID userId, int month, int year) {
        return getFinancialSnapshot(userId, month, year, null);
    }

    /**
     * Overload for callers that have already loaded the month's transactions (notably
     * {@link #buildChatContext}) — avoids a second identical query per chat turn. Pass
     * {@code null} to have them loaded here.
     *
     * <h3>Why the commitment sum changed</h3>
     *
     * This used to be {@code Σ tracking.monthlySaving} over projects whose {@code deadline} was in
     * the future, with no status check at all. Two things were wrong with that:
     *
     * <ol>
     *   <li><b>A project that no longer has a claim on the money still reduced safe spending.</b>
     *       {@code ProjectStatus.EXPIRED} documents the exact opposite — "the obligation is
     *       released (debt stops affecting safe spending) so a failed goal does not haunt the
     *       user" — and the same is true of COMPLETED / ABANDONED / FROZEN. The old filter only
     *       excluded them by accident, when their deadline happened to be in the past; an EXPIRED
     *       project frozen out at month 3 of a 24-month plan kept eating the user's headroom for
     *       another 21 months. Worse, expiry is applied lazily on read
     *       ({@code Project.expireIfDeadlinePassed}) and {@code findAllByUser} does not apply it,
     *       so a past-deadline project is still stored as ACTIVE — which is why the check below
     *       tests the deadline as well as the status rather than trusting either alone.</li>
     *   <li><b>It disagreed with the backend.</b> {@code ComputeBudgetAllocationUseCase} — the
     *       allocator this very chat calls when the user asks to cut their budget — sums
     *       {@code Project.calculateMonthlyCommitment()} over ACTIVE projects. Quoting a safe
     *       spending figure derived from a different sum than the plan attached to the same reply
     *       is how the two ended up contradicting each other on screen.</li>
     * </ol>
     *
     * The arithmetic itself is delegated to {@link EnvelopeCalculator}, the class whose whole
     * purpose is to be the only place this is computed.
     */
    public FinancialSnapshot getFinancialSnapshot(UUID userId, int month, int year,
                                                  List<Transaction> monthTransactions) {
        List<Transaction> txs = monthTransactions != null
                ? monthTransactions
                : loadMonthTransactions(userId, month, year);

        BigDecimal recordedIncome = sumOf(txs, t -> t.getType() == TransactionType.INCOME);
        BigDecimal recordedExpense = sumOf(txs, t -> t.getType() == TransactionType.EXPENSE);
        // Contributing to a saving goal is not consumption — the money left the wallet but it is
        // still the user's. Counting it against safe spending would tell someone who moved 5M into
        // their house fund that they have 5M less to live on this month, which is backwards.
        BigDecimal livingExpense = sumOf(txs, t -> t.getType() == TransactionType.EXPENSE
                && t.getCategory() != Category.PROJECT_CONTRIBUTION);

        List<Project> projects = projectRepository.findAllByUser(UserId.of(userId.toString()));
        LocalDate today = LocalDate.now();

        BigDecimal activeCommitment = BigDecimal.ZERO;
        BigDecimal releasedCommitment = BigDecimal.ZERO;
        for (Project p : projects) {
            if (p == null) continue;
            BigDecimal monthly = p.calculateMonthlyCommitment();
            if (monthly == null) continue;
            if (holdsMonthlyCommitment(p, today)) {
                activeCommitment = activeCommitment.add(monthly);
            } else {
                releasedCommitment = releasedCommitment.add(monthly);
            }
        }

        User user = userRepository.findById(userId).orElse(null);
        BigDecimal monthlyIncome = user != null && user.getIncome() != null ? user.getIncome() : BigDecimal.ZERO;
        boolean setupDone = user != null && user.hasCompletedFinancialSetup();

        BigDecimal safeSpending;
        BigDecimal savingCapacity = BigDecimal.ZERO;
        boolean overCommitted = false;
        boolean fallback = false;
        if (setupDone) {
            EnvelopeSnapshot envelope =
                    envelopeCalculator.computeFromCommitment(user, activeCommitment, livingExpense);
            safeSpending = envelope.safeSpending();
            savingCapacity = envelope.savingCapacity();
            overCommitted = envelope.overCommitted();
        } else {
            // No declared income means there is nothing to plan against. Remaining budget headroom
            // is a DIFFERENT quantity wearing the same name, so it is flagged as a stand-in — the
            // prompt labels it as such rather than presenting it as a planned allowance.
            List<Budget> budgets = budgetRepository.findByUserIdMonthYear(userId, month, year);
            BigDecimal totalBudgetLimit = budgets.stream()
                    .map(Budget::getAmountLimit)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalBudgetSpent = budgets.stream()
                    .map(b -> b.getSpent() != null ? b.getSpent() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            safeSpending = totalBudgetLimit.subtract(totalBudgetSpent).max(BigDecimal.ZERO);
            fallback = true;
        }

        BigDecimal remainingSafeSpending = safeSpending.subtract(livingExpense).max(BigDecimal.ZERO);

        int daysLeft = today.lengthOfMonth() - today.getDayOfMonth();
        return new FinancialSnapshot(
                monthlyIncome, safeSpending, daysLeft,
                recordedIncome, recordedExpense, livingExpense,
                activeCommitment, releasedCommitment, savingCapacity,
                remainingSafeSpending, overCommitted, setupDone, fallback);
    }

    /**
     * Whether this project still reserves money out of the user's month.
     *
     * <p>A project counts only while it is genuinely running: {@code status == ACTIVE} <em>and</em>
     * its deadline has not passed. The second half is not redundant — expiry is lazy
     * ({@link Project#expireIfDeadlinePassed()} runs when a project is read through the project
     * use cases, not when {@code findAllByUser} loads one), so a project past its deadline is
     * still stored as ACTIVE until something touches it. Treating it as live here would keep
     * charging the user for a goal that has already failed, which is precisely what
     * {@code ProjectStatus.EXPIRED} promises will not happen.
     *
     * <p>FROZEN is excluded too: a paused project refuses contributions
     * ({@code Project.requireContributable}), so it cannot consume this month's money either.
     */
    private boolean holdsMonthlyCommitment(Project project, LocalDate today) {
        return project.getStatus() == ProjectStatus.ACTIVE
                && !isFullyFunded(project)
                && project.getDeadline() != null
                && !project.getDeadline().isBefore(today);
    }

    /**
     * Target reached — the goal is paid for in full.
     *
     * <p>Checked separately from {@code status == COMPLETED} because completion, like expiry, is
     * not guaranteed to be stamped on the row this read path sees: contributions complete a
     * project synchronously ({@code AddProjectContributionUseCase}), but auto-saving and older
     * rows go through settlement, so {@code findAllByUser} can hand back a project that is
     * financially closed yet still stored as ACTIVE with months left on its deadline. Charging the
     * user's month for a goal they have already finished paying is exactly the complaint this
     * guards: the money is in, nothing more is owed, so it reserves nothing.
     */
    private boolean isFullyFunded(Project project) {
        return project.getTargetAmount() != null
                && project.getTargetAmount().amount() != null
                && project.getTargetAmount().amount().signum() > 0
                && project.remaining().amount().signum() <= 0;
    }

    /**
     * Status as the user should be told it, applying the same lazy rules the project screens apply
     * on read. Without this the chat would announce a long-dead goal as "đang chạy" purely because
     * nothing had opened it recently — and, symmetrically, would announce a goal the user already
     * finished funding as still running. Read-only — nothing is persisted from a chat turn.
     */
    private ProjectStatus effectiveStatus(Project project, LocalDate today) {
        boolean live = project.getStatus() == ProjectStatus.ACTIVE
                || project.getStatus() == ProjectStatus.FROZEN;
        // Fully funded wins over the deadline check: a goal that hit its target before the
        // deadline is COMPLETED, never EXPIRED. Project.expireIfDeadlinePassed draws the same
        // line (it refuses to expire a project at 100%).
        if (live && isFullyFunded(project)) {
            return ProjectStatus.COMPLETED;
        }
        if (live && project.getDeadline() != null && project.getDeadline().isBefore(today)
                && project.progressPercent().compareTo(BigDecimal.valueOf(100)) < 0) {
            return ProjectStatus.EXPIRED;
        }
        return project.getStatus();
    }

    private List<Transaction> loadMonthTransactions(UUID userId, int month, int year) {
        LocalDate monthStart = LocalDate.of(year, month, 1);
        return transactionRepository.findByUserIdWithFilters(
                userId, null, null,
                monthStart.atStartOfDay(),
                monthStart.withDayOfMonth(monthStart.lengthOfMonth()).atTime(23, 59, 59),
                null, null);
    }

    private BigDecimal sumOf(List<Transaction> txs, java.util.function.Predicate<Transaction> filter) {
        return txs.stream()
                .filter(Objects::nonNull)
                .filter(filter)
                .map(t -> t.getAmount() != null && t.getAmount().getValue() != null
                        ? t.getAmount().getValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Builds the full financial context block for the chat prompt.
     *
     * <p>Sections, in the order the assistant should reason through them: actual cash-flow for the
     * month, this month's spending broken down by category, the same figures for the months
     * before it, the planned spending allowance (and how much of it is genuinely left), the
     * automated spending analysis, budgets, individual transactions, projects, and groups.
     *
     * <h3>The two incomes</h3>
     * Both are printed, always, each with its own label and an explicit note on which questions it
     * may be used to answer. A reply that quotes the planned income while describing how the month
     * actually went — or the reverse — is the single most misleading thing this context can cause,
     * because both numbers are real and neither looks wrong on its own.
     *
     * <h3>Machine tokens</h3>
     * {@code budgetId} / {@code txId} / {@code projectId} and the enum names stay in this text
     * because the drafts the model fills in need them verbatim. Every one is paired with a plain
     * label from {@link FinancialTermFormatter} so the model always has the human wording within
     * reach; §10 of the prompt then forbids the raw token from appearing in {@code reply}.
     */
    public String buildChatContext(UUID userId, int month, int year) {
        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
        LocalDateTime start = monthStart.atStartOfDay();
        LocalDateTime end = monthEnd.atTime(23, 59, 59);

        List<Transaction> txs = transactionRepository.findByUserIdWithFilters(
                userId, null, null, start, end, null, null);

        // Budgets for the month
        List<Budget> budgets = budgetRepository.findByUserIdMonthYear(userId, month, year);

        // Projects
        List<Project> projects = projectRepository.findAllByUser(UserId.of(userId.toString()));
        LocalDate today = LocalDate.now();

        // The trailing history, loaded ONCE and shared by the month-by-month block and the
        // insight analysis below — they cover the same window, and a chat turn should not pay for
        // that query twice.
        YearMonth period = YearMonth.of(year, month);
        LocalDate asOf = period.equals(YearMonth.now()) ? today : period.atEndOfMonth();
        List<Transaction> history = loadTrailingWindow(userId, period, asOf);

        // Loaded once here and handed to the blocks that need it — the settlement forecast reads
        // the saving pace and the focus mode, the insight block needs the income.
        User contextUser = userRepository.findById(userId).orElse(null);

        // One snapshot, reusing the transactions already loaded above — every figure below comes
        // from it, so the prompt cannot contain two different answers to the same question.
        FinancialSnapshot snapshot = getFinancialSnapshot(userId, month, year, txs);
        BigDecimal totalIncome = snapshot.recordedIncome();
        BigDecimal totalExpense = snapshot.recordedExpense();
        BigDecimal monthlyIncome = snapshot.monthlyIncome();
        BigDecimal safeSpending = snapshot.safeSpending();
        int daysLeft = snapshot.daysLeft();

        // Cash-flow status this month — the anti-hallucination signal the prompt uses to decide
        // whether it's OK to recommend increased saving/topping up. Delegated to the shared
        // classifier below so the 10% SURPLUS/BALANCED/DEFICIT thresholds live in one place;
        // callers that need to gate their own behaviour (see ChatWithAIUseCase's proactive-
        // suggestion suppression) hit classifyCashFlow directly.
        BigDecimal cashFlowDelta = totalIncome.subtract(totalExpense);
        CashFlowStatus cashFlowEnum = classifyCashFlowFromTotals(totalIncome, totalExpense);
        String cashFlowStatus = cashFlowEnum.name();

        StringBuilder sb = new StringBuilder();

        // ── Dòng tiền THỰC TẾ tháng này ───────────────────────────────────────
        // Grouped and labelled so the LLM can't confuse actual cash-flow with planned headroom.
        // Every line is anchored to a formula the prompt's data dictionary can reference.
        sb.append("=== A. Dòng tiền THỰC TẾ tháng ").append(month).append("/").append(year)
                .append(" (từ giao dịch đã ghi nhận) ===\n");
        sb.append(String.format("Thu nhập THỰC TẾ đã ghi nhận trong tháng: %,.0f VND\n", totalIncome));
        if (!snapshot.incomeRecordedThisMonth()) {
            // Zero recorded income almost never means "earned nothing" — it means the salary row
            // hasn't been entered. Saying so here stops the assistant from announcing a
            // catastrophic income collapse on the 3rd of the month.
            sb.append("  LƯU Ý: chưa có giao dịch thu nhập nào trong tháng này. Điều này thường có\n")
                    .append("  nghĩa là người dùng CHƯA GHI NHẬN khoản thu, KHÔNG phải là họ không có\n")
                    .append("  thu nhập. Tuyệt đối không kết luận thu nhập bằng 0 hay thu nhập sụt giảm.\n");
        }
        sb.append(String.format("Chi tiêu thực tế tháng này (gồm cả tiền góp dự án): %,.0f VND\n", totalExpense));
        sb.append(String.format("Trong đó chi tiêu sinh hoạt (KHÔNG tính tiền góp vào dự án): %,.0f VND\n",
                snapshot.livingExpense()));
        sb.append(String.format("Chênh lệch (thu nhập thực tế - chi tiêu thực tế): %,.0f VND\n", cashFlowDelta));
        sb.append("Trạng thái dòng tiền tháng: ").append(cashFlowStatus)
                .append(" (").append(FinancialTermFormatter.cashFlow(cashFlowEnum, false)).append(")\n");

        // ── Chi tiêu theo danh mục tháng này ──────────────────────────────────
        // The month's totals alone cannot answer "tình hình tài chính của tôi thế nào" — where the
        // money went is half the answer, and the assistant used to have to reconstruct it from the
        // capped transaction list in block F (which is partial, so any sum from it is wrong) or
        // from block E (which only knows the categories that happen to have a budget). Emitted
        // unconditionally, including the "nothing recorded yet" case, so the reply states that
        // fact instead of quietly omitting the section and sounding like there was nothing to say.
        appendCategoryBreakdown(sb, txs, budgets, month, year, totalExpense);

        // ── Các tháng trước ───────────────────────────────────────────────────
        appendMonthlyHistory(sb, history, period);

        // ── Thu nhập LÝ THUYẾT theo hồ sơ ─────────────────────────────────────
        // Printed as its own section rather than one more line in the cash-flow block, because it
        // is a different KIND of number: a standing assumption from onboarding, not an observation.
        sb.append("\n=== B. Thu nhập LÝ THUYẾT theo hồ sơ người dùng (thiết lập tài chính) ===\n");
        if (snapshot.financialSetupDone()) {
            sb.append(String.format("Thu nhập hàng tháng theo hồ sơ: %,.0f VND\n", monthlyIncome));
            sb.append(String.format("Khả năng tiết kiệm theo nhịp độ đã chọn: %,.0f VND/tháng\n",
                    snapshot.savingCapacity()));
            BigDecimal gap = snapshot.incomeVsPlan();
            if (snapshot.incomeRecordedThisMonth()) {
                sb.append(String.format(
                        "Chênh lệch thu nhập thực tế so với hồ sơ: %,.0f VND (%s)\n",
                        gap, gap.signum() >= 0 ? "thực tế cao hơn hoặc bằng hồ sơ"
                                : "thực tế đang THẤP HƠN mức hồ sơ"));
            }
        } else {
            sb.append("Người dùng CHƯA hoàn tất thiết lập tài chính, nên KHÔNG có thu nhập lý thuyết.\n")
                    .append("Mọi con số kế hoạch bên dưới chỉ là ước lượng tạm; hãy nói rõ điều này khi\n")
                    .append("người dùng hỏi về hạn mức chi tiêu, và gợi ý họ hoàn tất thiết lập tài chính.\n");
        }
        sb.append("QUY TẮC DÙNG HAI CON SỐ THU NHẬP:\n")
                .append("  - Đánh giá tháng này ĐANG diễn ra thế nào (dư/thiếu, có chi vượt không):\n")
                .append("    dùng THU NHẬP THỰC TẾ ở mục A.\n")
                .append("  - Tính hạn mức/kế hoạch dài hạn (chi tiêu an toàn, cam kết dự án, dự án mới):\n")
                .append("    dùng THU NHẬP LÝ THUYẾT ở mục B.\n")
                .append("  - TUYỆT ĐỐI KHÔNG trộn hai con số trong cùng một phép tính, và khi nêu ra\n")
                .append("    phải nói rõ đang nói tới con số nào.\n");

        // ── Chi tiêu an toàn ──────────────────────────────────────────────────
        // The formula is spelled out and matched to the backend allocator. "Còn lại thực tế" is
        // precomputed here rather than left to the model, because the model doing this subtraction
        // itself is exactly how "bạn còn X để chi" ended up wrong.
        sb.append("\n=== C. Chi tiêu an toàn (hạn mức chi tiêu hàng tháng) ===\n");
        if (snapshot.safeSpendingIsFallback()) {
            sb.append(String.format(
                    "Chi tiêu an toàn (TẠM TÍNH từ ngân sách còn lại, vì chưa có thu nhập hồ sơ): %,.0f VND\n",
                    safeSpending));
            sb.append("  Công thức tạm: tổng hạn mức các ngân sách tháng - tổng đã chi trong các ngân sách đó.\n");
        } else {
            sb.append(String.format("Chi tiêu an toàn hàng tháng theo kế hoạch: %,.0f VND\n", safeSpending));
            sb.append("  Công thức: MAX(0, thu nhập lý thuyết theo hồ sơ - tổng cam kết hàng tháng\n")
                    .append("  của các dự án CÒN HIỆU LỰC). Đây đúng là công thức backend dùng khi lập\n")
                    .append("  kế hoạch ngân sách, nên con số này luôn khớp với màn hình ngân sách.\n");
        }
        sb.append(String.format("Tổng cam kết hàng tháng của các dự án CÒN HIỆU LỰC: %,.0f VND\n",
                snapshot.activeCommitment()));
        if (snapshot.releasedCommitment().compareTo(BigDecimal.ZERO) > 0) {
            sb.append(String.format(
                    "Cam kết đã ĐƯỢC GIẢI PHÓNG (dự án hết hạn/hoàn tất/đã huỷ/tạm dừng): %,.0f VND\n",
                    snapshot.releasedCommitment()));
            sb.append("  Các dự án này KHÔNG còn bị trừ vào chi tiêu an toàn nữa.\n");
        }
        sb.append(String.format("Đã chi cho sinh hoạt trong tháng: %,.0f VND\n", snapshot.livingExpense()));
        sb.append(String.format("=> CHI TIÊU AN TOÀN CÒN LẠI THỰC TẾ: %,.0f VND\n",
                snapshot.remainingSafeSpending()));
        sb.append("  Đây là con số DUY NHẤT được dùng khi trả lời \"tôi còn bao nhiêu để chi\".\n")
                .append("  KHÔNG tự cộng trừ lại; KHÔNG dùng hạn mức kế hoạch ở trên thay cho nó.\n");
        if (snapshot.overCommitted()) {
            sb.append("CẢNH BÁO: tổng cam kết dự án hàng tháng đang VƯỢT thu nhập theo hồ sơ —\n")
                    .append("  người dùng đã hứa tiết kiệm nhiều hơn số họ kiếm được.\n");
        }
        sb.append(String.format("Hôm nay: %s. Số ngày còn lại trong tháng: %d\n", today, daysLeft));

        // ── Phân tích chi tiêu tự động (insight) ──────────────────────────────
        // The same findings GET /api/v1/insights returns. Without this the assistant had to infer
        // trends from the 15 transaction rows below — which is how it ended up describing a
        // "trend" the Insights screen disagreed with.
        appendInsights(sb, contextUser, budgets, history, asOf);

        // ── Ngân sách tháng ───────────────────────────────────────────────────
        // status is alertLevel (SAFE/CAUTION/WARNING/EXCEEDED) — surfaced so the AI never has
        // to compute the pressure level itself, with its plain-language label alongside so the
        // reply can name the state without quoting the enum.
        if (!budgets.isEmpty()) {
            sb.append("\n=== E. Ngân sách tháng ").append(month).append("/").append(year).append(" ===\n");
            budgets.forEach(b -> {
                BigDecimal spent = b.getSpent() != null ? b.getSpent() : BigDecimal.ZERO;
                BigDecimal pct = b.getAmountLimit().compareTo(BigDecimal.ZERO) > 0
                        ? spent.multiply(BigDecimal.valueOf(100))
                        .divide(b.getAmountLimit(), 1, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                Budget.AlertLevel level = b.getAlertLevel(spent);
                sb.append(String.format(
                        "  budgetId=%s category=%s (%s) spent=%,.0f limit=%,.0f (%.1f%%) status=%s (%s)\n",
                        b.getId(), b.getCategory().name(),
                        FinancialTermFormatter.category(b.getCategory(), false),
                        spent, b.getAmountLimit(), pct,
                        level, FinancialTermFormatter.alertLevel(level, false)));
            });
        } else {
            sb.append("\nChưa thiết lập ngân sách tháng ").append(month).append("/").append(year).append(".\n");
        }

        // ── Giao dịch trong tháng ─────────────────────────────────────────────
        // Individual rows, not just the totals above. Two things need them: answering "what did I
        // spend on last week?" without inventing numbers, and — critically — giving the assistant
        // real txId values, since editing or deleting a transaction through chat is impossible
        // without one to point at. Capped at RECENT_TRANSACTION_LIMIT most-recent-first so a heavy
        // month can't crowd the budgets/projects sections out of the prompt; the cap is stated in
        // the text so the LLM knows the list may be partial and never claims it's exhaustive.
        //
        // locked=true marks PROJECT_CONTRIBUTION/PROJECT_REFUND rows, which the project machinery
        // owns — TransactionChangeService refuses to edit or delete them (see its class doc), so
        // the prompt is told upfront rather than letting the assistant offer a change that would
        // silently produce no card.
        appendTransactions(sb, txs, month, year);

        // ── Dự án ─────────────────────────────────────────────────────────────
        // linkedToGroup marker is the CRITICAL new field: sub-personal projects
        // (Project.isSubPersonal(), i.e. groupProjectId != null) are half of a group commitment,
        // so the chatbot MUST NOT propose structural or tracking changes for them via chat —
        // otherwise a "yes" reply silently mutates a shared group goal. The prompt (§5) rejects
        // suggestions for these; ChatWithAIUseCase.buildStructuredResponse enforces the same
        // rule at the backend as defense-in-depth.
        //
        // ACTIVE projects are listed first so they aren't crowded out of the cap by stale ones.
        //
        // status is the EFFECTIVE status (see effectiveStatus): a project past its deadline reads
        // as expired here even though the row is still stored as ACTIVE, because expiry is applied
        // lazily by the project screens and never by this read path. countsTowardSafeSpending says
        // outright whether the project still eats into the monthly allowance, so the assistant
        // never has to work that out from the status and deadline itself.
        if (!projects.isEmpty()) {
            sb.append("\n=== G. Dự án ===\n");
            projects.stream()
                    .sorted(Comparator.comparingInt(p -> effectiveStatus(p, today) == ProjectStatus.ACTIVE ? 0 : 1))
                    .limit(5)
                    .forEach(p -> {
                        // Floored at zero: a past-deadline project would otherwise report
                        // "monthsLeft=-2", which reads as a time remaining rather than a deadline
                        // already missed. The status field beside it carries that fact properly.
                        long monthsLeft = Math.max(0, ChronoUnit.MONTHS.between(today, p.getDeadline()));
                        ProjectTracking tracking = p.getTracking();
                        BigDecimal monthlySaving = tracking != null ? tracking.getMonthlySaving() : BigDecimal.ZERO;
                        BigDecimal moneyOwed = tracking != null ? tracking.getMoneyOwed() : BigDecimal.ZERO;
                        ProjectStatus status = effectiveStatus(p, today);
                        boolean counts = holdsMonthlyCommitment(p, today);
                        // A project that no longer holds a claim on the month reports
                        // monthlyCommitment=0, not the amount it used to cost. A completed goal
                        // still showing "monthlyCommitment=1,666,667" was being read back as money
                        // the user still owes every month — the plan figure is kept alongside,
                        // clearly labelled as released, so the assistant can explain the change
                        // without ever adding it to this month's spending.
                        BigDecimal plannedCommitment = p.calculateMonthlyCommitment();
                        sb.append(String.format(
                                "  %s: projectId=%s status=%s (%s) priority=%s target=%s remaining=%s "
                                        + "monthlyCommitment=%,.0f monthlySaving=%,.0f moneyOwed=%,.0f "
                                        + "monthsLeft=%d countsTowardSafeSpending=%s linkedToGroup=%s%s\n",
                                p.getName(),
                                p.getId(),
                                status,
                                FinancialTermFormatter.projectStatus(status, false),
                                p.getPriority(),
                                p.getTargetAmount(),
                                p.remaining(),
                                counts ? plannedCommitment : BigDecimal.ZERO,
                                monthlySaving,
                                moneyOwed,
                                monthsLeft,
                                counts,
                                p.isSubPersonal(),
                                !counts && plannedCommitment.signum() > 0
                                        ? String.format(" [dự án đã đóng/không còn hiệu lực — mức cam kết cũ "
                                        + "%,.0f VND/tháng ĐÃ ĐƯỢC GIẢI PHÓNG, không còn là khoản chi hàng tháng]",
                                        plannedCommitment)
                                        : ""));
                    });
            sb.append("  Chỉ những dòng countsTowardSafeSpending=true mới bị trừ vào chi tiêu an toàn.\n")
                    .append("  monthlyCommitment=0 nghĩa là dự án đó KHÔNG còn chiếm khoản chi hàng tháng nào —\n")
                    .append("  TUYỆT ĐỐI KHÔNG cộng nó vào chi tiêu/cam kết hàng tháng của người dùng.\n");
        }

        appendGroups(sb, userId);

        // ── Tất toán cuối tháng ───────────────────────────────────────────────
        appendSettlementForecast(sb, contextUser, projects, txs, snapshot, period);

        return sb.toString();
    }

    /**
     * What the month-end settlement run will do to this user's projects, computed the way the
     * engine computes it.
     *
     * <p>The assistant could see {@code moneyOwed} and {@code monthlySaving} on the project rows
     * but had no idea where either number comes from or what happens to them on the 1st: the whole
     * settlement process — the auto-saving credit, the debt/surplus split, the freeze at
     * {@value SettlementEngine#FREEZE_DEBT_MULTIPLIER}× monthly saving, the expiry after
     * {@value SettlementEngine#MAX_FROZEN_MONTHS} frozen months — appeared nowhere in the prompt.
     * Asked "cuối tháng tất toán thì sao", it could only invent an answer, and a plausible-sounding
     * invention about someone's savings being penalised is about the worst output this chat has.
     *
     * <h3>Why every figure is computed here rather than left to the model</h3>
     * Settlement does NOT judge the month by the numbers in block A. It excludes the synthetic
     * project ledger rows from both sides, and — the part no LLM would ever guess — falls back to
     * the profile income when the month recorded none. Its saving expectation is the sum of
     * {@code tracking.monthlySaving} capped by the saving pace, which is a different quantity from
     * block C's commitment sum. Any of those differences is enough to make a hand-derived answer
     * wrong, so the projection is spelled out and the prompt is told to quote it verbatim.
     *
     * <p>Stated as a forecast, not a fact: the month is usually still running, and every number
     * below moves with each transaction recorded before the 1st.
     */
    private void appendSettlementForecast(StringBuilder sb, User user, List<Project> projects,
                                          List<Transaction> txs, FinancialSnapshot snapshot,
                                          YearMonth period) {
        if (user == null || !snapshot.financialSetupDone()) return;

        LocalDate settlementDate = period.plusMonths(1).atDay(1);

        sb.append("\n=== I. Tất toán cuối tháng ").append(period.getMonthValue()).append("/")
                .append(period.getYear()).append(" (DỰ BÁO) ===\n");

        if (!settlementProperties.isEnabled()) {
            sb.append("Hệ thống tất toán tự động đang TẮT ở phiên bản này: cuối tháng KHÔNG có đợt\n")
                    .append("tất toán nào chạy, không tự trừ tiền, không tự cộng nợ, không tự đóng băng dự án.\n")
                    .append("Tiến độ dự án chỉ thay đổi khi người dùng tự bấm góp tiền. Nếu người dùng hỏi về\n")
                    .append("tất toán cuối tháng, nói đúng như vậy.\n");
            return;
        }

        // Settlement's own view of the month: synthetic project rows excluded on both sides, and
        // the profile income standing in when nothing was recorded.
        BigDecimal settlementIncome = sumOf(txs, t -> t.getType() == TransactionType.INCOME
                && t.getCategory() != Category.PROJECT_REFUND);
        boolean incomeFallback = settlementIncome.signum() == 0;
        if (incomeFallback) {
            settlementIncome = snapshot.monthlyIncome();
        }
        BigDecimal settlementExpense = snapshot.livingExpense();
        BigDecimal actualSavings = settlementIncome.subtract(settlementExpense);

        BigDecimal totalSavingGoals = BigDecimal.ZERO;
        for (Project p : projects) {
            if (p == null || p.getStatus() != ProjectStatus.ACTIVE) continue;
            ProjectTracking t = p.getTracking();
            if (t != null && t.getMonthlySaving() != null) {
                totalSavingGoals = totalSavingGoals.add(t.getMonthlySaving());
            }
        }
        BigDecimal capacity = user.getSavingCapacity() != null ? user.getSavingCapacity() : BigDecimal.ZERO;
        BigDecimal effectiveGoals = SettlementEngine.effectiveGoals(totalSavingGoals, capacity);
        boolean penaltiesEnabled = user.getFocusMode() != Focus_Mode.TRACK_ONLY;

        sb.append(String.format("Ngày tất toán kế tiếp: %s (00:00), tất toán cho tháng %d/%d.\n",
                settlementDate, period.getMonthValue(), period.getYear()));
        sb.append(String.format(
                "Thu nhập dùng để tất toán: %,.0f VND%s\n", settlementIncome,
                incomeFallback
                        ? " (chưa ghi nhận khoản thu nào nên hệ thống LẤY THU NHẬP THEO HỒ SƠ để tất toán)"
                        : " (tổng giao dịch thu, KHÔNG tính tiền hoàn từ dự án)"));
        sb.append(String.format(
                "Chi tiêu dùng để tất toán: %,.0f VND (KHÔNG tính tiền góp vào dự án — góp tiền không bị coi là tiêu)\n",
                settlementExpense));
        sb.append(String.format("Tiết kiệm thực tế = thu - chi: %,.0f VND\n", actualSavings));
        sb.append(String.format(
                "Mức tiết kiệm hệ thống kỳ vọng tháng này: %,.0f VND\n", effectiveGoals));
        sb.append(String.format(
                "  = MIN(tổng nhịp góp hàng tháng của dự án đang chạy %,.0f VND; khả năng tiết kiệm "
                        + "theo nhịp độ %,.0f VND)\n", totalSavingGoals, capacity));

        if (actualSavings.compareTo(effectiveGoals) >= 0) {
            BigDecimal surplus = actualSavings.subtract(effectiveGoals);
            sb.append(String.format("DỰ BÁO: ĐẠT kỳ vọng, dư %,.0f VND.\n", surplus));
            sb.append("  Phần dư sẽ được dùng để TRẢ NỢ các dự án trước (ưu tiên cao trước).\n");
            sb.append(user.isAutoInvestSurplus()
                    ? "  Sau khi hết nợ, phần còn lại được TỰ ĐỘNG GÓP THÊM vào dự án (người dùng đã bật tự động đầu tư phần dư).\n"
                    : "  Người dùng ĐANG TẮT tự động đầu tư phần dư, nên sau khi trả nợ xong phần còn lại giữ nguyên là tiền mặt tự do.\n");
        } else if (penaltiesEnabled) {
            BigDecimal shortfall = effectiveGoals.subtract(actualSavings);
            sb.append(String.format("DỰ BÁO: THIẾU %,.0f VND so với kỳ vọng.\n", shortfall));
            sb.append("  Khoản thiếu này sẽ được ghi thành NỢ (moneyOwed) của các dự án, chia từ dự án\n")
                    .append("  ưu tiên THẤP lên cao. Đây là nợ nội bộ với chính mục tiêu tiết kiệm, KHÔNG\n")
                    .append("  phải nợ tiền với bên nào và không có lãi.\n");
        } else {
            sb.append(String.format(
                    "DỰ BÁO: thiếu %,.0f VND so với kỳ vọng, NHƯNG người dùng đang ở chế độ chỉ theo dõi\n",
                    effectiveGoals.subtract(actualSavings)))
                    .append("  (TRACK_ONLY) nên hệ thống KHÔNG ghi nợ, KHÔNG phạt, KHÔNG đóng băng dự án.\n");
        }

        sb.append("Mỗi dự án đang chạy còn được tự động ghi nhận một khoản góp bằng đúng nhịp góp\n")
                .append("hàng tháng của nó (không vượt quá phần còn thiếu), rồi cập nhật số tháng còn lại.\n");

        List<Project> settled = projects.stream()
                .filter(p -> p != null && p.getTracking() != null)
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE || p.getStatus() == ProjectStatus.FROZEN)
                .limit(5)
                .toList();
        if (!settled.isEmpty()) {
            sb.append("Trạng thái nợ/đóng băng từng dự án sẽ được tất toán:\n");
            for (Project p : settled) {
                ProjectTracking t = p.getTracking();
                BigDecimal owed = t.getMoneyOwed() != null ? t.getMoneyOwed() : BigDecimal.ZERO;
                BigDecimal monthlySaving = t.getMonthlySaving() != null ? t.getMonthlySaving() : BigDecimal.ZERO;
                BigDecimal freezeAt = SettlementEngine.freezeThreshold(monthlySaving);
                if (p.getStatus() == ProjectStatus.FROZEN) {
                    sb.append(String.format(
                            "  %s: ĐANG TẠM DỪNG, nợ=%,.0f VND, đã tạm dừng %d/%d tháng. Hết nợ thì tự chạy\n"
                                    + "    lại; nếu vẫn còn nợ sau %d tháng tạm dừng thì dự án chuyển thành quá hạn.\n",
                            p.getName(), owed, t.getFrozenMonths(), SettlementEngine.MAX_FROZEN_MONTHS,
                            SettlementEngine.MAX_FROZEN_MONTHS));
                } else {
                    sb.append(String.format(
                            "  %s: nợ=%,.0f VND, sẽ bị TẠM DỪNG khi nợ chạm %,.0f VND (=%d lần nhịp góp %,.0f VND);"
                                    + " còn cách %,.0f VND.\n",
                            p.getName(), owed, freezeAt, SettlementEngine.FREEZE_DEBT_MULTIPLIER,
                            monthlySaving, freezeAt.subtract(owed).max(BigDecimal.ZERO)));
                }
            }
        }
        sb.append("Sau khi tất toán, dự án đủ mục tiêu chuyển thành đã hoàn tất; dự án hết số tháng\n")
                .append("còn lại mà chưa đủ mục tiêu chuyển thành quá hạn.\n");
        sb.append("LƯU Ý BẮT BUỘC: đây là DỰ BÁO theo số liệu tới thời điểm hiện tại, tháng có thể\n")
                .append("chưa kết thúc — mọi giao dịch ghi thêm trước ngày tất toán đều làm các con số này\n")
                .append("thay đổi. Khi trả lời phải nói rõ là dự báo, và TRÍCH NGUYÊN các con số ở khối này,\n")
                .append("KHÔNG tự tính lại từ khối A hay khối C (cách tính của tất toán khác).\n");
    }

    /**
     * The trailing transaction window shared by the month-by-month history block and the insight
     * analysis — the same window {@code GET /api/v1/insights} works over, so the two can never
     * describe different months. Best-effort: this is enrichment, and a lookup failure must cost
     * the user the two enriched blocks, not their whole turn.
     */
    private List<Transaction> loadTrailingWindow(UUID userId, YearMonth period, LocalDate asOf) {
        try {
            LocalDateTime windowStart = period.minusMonths(SpendingInsightAnalyzer.TRAILING_WINDOW)
                    .atDay(1).atStartOfDay();
            LocalDateTime windowEnd = asOf.atTime(23, 59, 59);
            return transactionRepository.findByUserIdWithFilters(
                    userId, null, null, windowStart, windowEnd, null, null);
        } catch (Exception e) {
            log.warn("UserFinancialDataService: could not load trailing history for user {}: {}",
                    userId, e.getMessage());
            return List.of();
        }
    }

    /**
     * This month's expenses split by category.
     *
     * <p>Answering "how am I doing financially?" with two totals and a status word is not an
     * answer — the user wants to know <em>where</em> the money went, and every follow-up ("should
     * I cut something?", "is eating out the problem?") needs the split. The assistant had no
     * trustworthy way to produce it: block F is capped at the most recent rows, so summing it
     * undercounts, and block E only covers categories that happen to have a budget.
     *
     * <p>The empty case is printed, loudly, rather than skipped. A missing section reads to the
     * model as "nothing worth mentioning", and it would then describe the month's spending in
     * general terms — for a user who simply has not logged anything yet. The line says outright
     * that nothing has been recorded, which is what the reply must say too.
     *
     * <p>Percentages are of total expense (the figure in block A), including project
     * contributions, so every row is a share of the same denominator; contribution rows are
     * flagged inline because they are savings, not consumption.
     */
    private void appendCategoryBreakdown(StringBuilder sb, List<Transaction> txs, List<Budget> budgets,
                                         int month, int year, BigDecimal totalExpense) {
        sb.append("\n=== A2. Chi tiêu theo danh mục trong tháng ").append(month).append("/").append(year)
                .append(" ===\n");

        Map<Category, BigDecimal> spentByCategory = new EnumMap<>(Category.class);
        Map<Category, Integer> countByCategory = new EnumMap<>(Category.class);
        BigDecimal uncategorized = BigDecimal.ZERO;
        for (Transaction t : txs) {
            if (t == null || t.getType() != TransactionType.EXPENSE) continue;
            BigDecimal amount = t.getAmount() != null && t.getAmount().getValue() != null
                    ? t.getAmount().getValue() : BigDecimal.ZERO;
            if (t.getCategory() == null) {
                uncategorized = uncategorized.add(amount);
                continue;
            }
            spentByCategory.merge(t.getCategory(), amount, BigDecimal::add);
            countByCategory.merge(t.getCategory(), 1, Integer::sum);
        }

        if (spentByCategory.isEmpty() && uncategorized.signum() == 0) {
            sb.append("HIỆN TẠI CHƯA GHI NHẬN GIAO DỊCH CHI TIÊU PHÁT SINH TỪ BẤT KỲ DANH MỤC NÀO\n")
                    .append("TRONG THÁNG NÀY.\n")
                    .append("  BẮT BUỘC: khi người dùng hỏi về tình hình tài chính, mức chi tiêu, hay chi tiêu\n")
                    .append("  theo danh mục, phải nói rõ rằng hiện chưa ghi nhận giao dịch phát sinh từ các\n")
                    .append("  danh mục trong tháng này, rồi mời họ ghi nhận giao dịch để có số liệu.\n")
                    .append("  TUYỆT ĐỐI KHÔNG bịa mức chi của bất kỳ danh mục nào, và KHÔNG suy ra mức chi\n")
                    .append("  danh mục từ ngân sách hay từ các tháng trước.\n");
        } else {
            spentByCategory.entrySet().stream()
                    .sorted(Map.Entry.<Category, BigDecimal>comparingByValue().reversed())
                    .forEach(e -> {
                        BigDecimal pct = totalExpense.signum() > 0
                                ? e.getValue().multiply(BigDecimal.valueOf(100))
                                .divide(totalExpense, 1, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO;
                        sb.append(String.format("  category=%s (%s) spent=%,.0f VND (%.1f%% tổng chi tiêu, %d giao dịch)%s\n",
                                e.getKey().name(),
                                FinancialTermFormatter.category(e.getKey(), false),
                                e.getValue(), pct, countByCategory.getOrDefault(e.getKey(), 0),
                                PROJECT_LEDGER_CATEGORIES.contains(e.getKey())
                                        ? " [tiền góp/hoàn dự án — KHÔNG phải chi tiêu sinh hoạt]" : ""));
                    });
            if (uncategorized.signum() > 0) {
                sb.append(String.format("  (chưa phân loại danh mục: %,.0f VND)\n", uncategorized));
            }
            sb.append("  Đây là TOÀN BỘ chi tiêu tháng này đã gom theo danh mục (không bị cắt bớt như\n")
                    .append("  danh sách giao dịch ở khối F). Khi nói về chi tiêu theo danh mục, trích các dòng\n")
                    .append("  này; các danh mục KHÔNG xuất hiện ở đây nghĩa là chưa phát sinh giao dịch nào\n")
                    .append("  trong tháng.\n");
        }

        // Budgeted-but-untouched categories: the user planned for them, so "no spending yet" is a
        // fact about their month, not an absence of data. Without this line the assistant can only
        // see the budget row (spent=0) and has to guess whether that means unused or unrecorded.
        List<String> untouched = budgets.stream()
                .map(Budget::getCategory)
                .filter(Objects::nonNull)
                .distinct()
                .filter(c -> !spentByCategory.containsKey(c))
                .map(c -> c.name() + " (" + FinancialTermFormatter.category(c, false) + ")")
                .toList();
        if (!untouched.isEmpty()) {
            sb.append("  Danh mục CÓ ngân sách nhưng CHƯA ghi nhận giao dịch nào trong tháng: ")
                    .append(String.join(", ", untouched)).append("\n");
        }
    }

    /**
     * The same month-level figures as block A, for each of the {@value #HISTORY_MONTHS} months
     * before the one being discussed.
     *
     * <p>Every number the assistant could see was scoped to the current month, so any question
     * that reaches backwards — "tháng trước tôi tiêu bao nhiêu?", "tháng này tôi có tiêu nhiều hơn
     * không?", "mấy tháng nay tôi có tháng nào âm không?" — had nothing behind it. Block D's
     * analyzer findings partly covered trends, but only where a finding actually fired, and they
     * are per-category verdicts rather than the month's cash-flow. This is the raw monthly ledger
     * summary, precomputed the same way as block A (same living-expense rule, same
     * SURPLUS/BALANCED/DEFICIT thresholds) so a comparison across months compares like with like.
     *
     * <p>Months with no transactions at all are omitted rather than printed as zeroes: a month
     * with nothing recorded means the user was not tracking then, and a row of zeroes reads as a
     * month where they earned and spent nothing.
     */
    private void appendMonthlyHistory(StringBuilder sb, List<Transaction> history, YearMonth period) {
        YearMonth oldest = period.minusMonths(HISTORY_MONTHS);

        Map<YearMonth, List<Transaction>> byMonth = new TreeMap<>();
        for (Transaction t : history) {
            if (t == null || t.getDate() == null) continue;
            YearMonth ym = YearMonth.from(t.getDate());
            // Strictly BEFORE the month under discussion — that month is block A's job.
            if (!ym.isBefore(period) || ym.isBefore(oldest)) continue;
            byMonth.computeIfAbsent(ym, k -> new ArrayList<>()).add(t);
        }

        sb.append("\n=== A3. Tình hình tài chính ").append(HISTORY_MONTHS)
                .append(" tháng TRƯỚC tháng ").append(period.getMonthValue()).append("/")
                .append(period.getYear()).append(" ===\n");

        if (byMonth.isEmpty()) {
            sb.append("Chưa có giao dịch nào được ghi nhận trong các tháng trước.\n")
                    .append("  Khi người dùng hỏi về tháng trước hay so sánh với các tháng trước, nói rõ là\n")
                    .append("  hệ thống chưa có dữ liệu của những tháng đó. TUYỆT ĐỐI KHÔNG bịa số liệu tháng cũ.\n");
            return;
        }

        List<YearMonth> months = new ArrayList<>(byMonth.keySet());
        Collections.reverse(months);  // gần nhất trước

        BigDecimal sumIncome = BigDecimal.ZERO;
        BigDecimal sumLiving = BigDecimal.ZERO;
        for (YearMonth ym : months) {
            List<Transaction> monthTxs = byMonth.get(ym);
            BigDecimal income = sumOf(monthTxs, t -> t.getType() == TransactionType.INCOME);
            BigDecimal expense = sumOf(monthTxs, t -> t.getType() == TransactionType.EXPENSE);
            BigDecimal living = sumOf(monthTxs, t -> t.getType() == TransactionType.EXPENSE
                    && t.getCategory() != Category.PROJECT_CONTRIBUTION);
            CashFlowStatus status = classifyCashFlowFromTotals(income, expense);
            sumIncome = sumIncome.add(income);
            sumLiving = sumLiving.add(living);

            sb.append(String.format(
                    "  Tháng %d/%d: thu nhập=%,.0f VND chi tiêu=%,.0f VND (sinh hoạt=%,.0f VND) "
                            + "chênh lệch=%,.0f VND trạng thái=%s (%s)\n",
                    ym.getMonthValue(), ym.getYear(), income, expense, living,
                    income.subtract(expense), status.name(),
                    FinancialTermFormatter.cashFlow(status, false)));

            String top = topCategories(monthTxs);
            if (top != null) sb.append("      Chi nhiều nhất: ").append(top).append("\n");
        }

        int n = months.size();
        sb.append(String.format(
                "  Trung bình %d tháng có dữ liệu: thu nhập=%,.0f VND/tháng, chi tiêu sinh hoạt=%,.0f VND/tháng\n",
                n,
                sumIncome.divide(BigDecimal.valueOf(n), 0, RoundingMode.HALF_UP),
                sumLiving.divide(BigDecimal.valueOf(n), 0, RoundingMode.HALF_UP)));
        sb.append("  Dùng khối này khi người dùng hỏi về tháng trước, so sánh giữa các tháng, hay hỏi\n")
                .append("  \"dạo này tôi thế nào\". LƯU Ý: tháng đang xét ở khối A có thể CHƯA kết thúc, nên khi\n")
                .append("  so sánh với các tháng đủ ngày ở đây phải nói rõ tháng này mới đi được một phần.\n")
                .append("  Chỉ có ").append(HISTORY_MONTHS).append(" tháng gần nhất; KHÔNG nói gì về các tháng xa hơn.\n");
    }

    /** The two biggest expense categories of a past month — enough to say what drove it without
     *  turning each history row into a full breakdown. Null when the month had no categorised
     *  expense at all. */
    private String topCategories(List<Transaction> monthTxs) {
        Map<Category, BigDecimal> byCategory = new EnumMap<>(Category.class);
        for (Transaction t : monthTxs) {
            if (t == null || t.getType() != TransactionType.EXPENSE || t.getCategory() == null) continue;
            BigDecimal amount = t.getAmount() != null && t.getAmount().getValue() != null
                    ? t.getAmount().getValue() : BigDecimal.ZERO;
            byCategory.merge(t.getCategory(), amount, BigDecimal::add);
        }
        if (byCategory.isEmpty()) return null;
        return byCategory.entrySet().stream()
                .sorted(Map.Entry.<Category, BigDecimal>comparingByValue().reversed())
                .limit(2)
                .map(e -> String.format("category=%s (%s) %,.0f VND",
                        e.getKey().name(), FinancialTermFormatter.category(e.getKey(), false), e.getValue()))
                .collect(Collectors.joining("; "));
    }

    /** How many completed months before the one under discussion are summarised in block A3.
     *  Matched to the analyzer's trailing window so both blocks describe the same stretch of
     *  history and one query serves both. */
    private static final int HISTORY_MONTHS = SpendingInsightAnalyzer.TRAILING_WINDOW;

    /** How many analyzer findings reach the prompt. Ordered most-severe first, so the cap drops
     *  the least urgent rows rather than an arbitrary slice; enough to characterise the month
     *  without letting a noisy history push the budgets/projects sections out of the context. */
    private static final int MAX_INSIGHTS_LISTED = 6;

    /**
     * Folds the deterministic spending analysis into the prompt.
     *
     * <p>These are the same findings {@code GET /api/v1/insights} returns — computed by the same
     * {@link SpendingInsightAnalyzer}, over the same trailing window (loaded once by
     * {@link #loadTrailingWindow} and shared with the month-by-month history block). Before this existed, the
     * assistant could see only the current month's 15 most recent transaction rows, so any
     * question about a trend ("am I spending more on food lately?", "will I blow my budget?") was
     * answered by eyeballing those rows. That produced statements the Insights screen flatly
     * contradicted, and month-end projections that were simply invented. Now the projection, the
     * baseline and the anomaly verdict all arrive precomputed, and §2 of the prompt tells the model
     * to quote them rather than derive its own.
     *
     * <p>Rendered in Vietnamese prose rather than as a metrics map: the model's job here is to
     * relay a finding, and a sentence is far harder to mis-assemble than
     * {@code {zScore: 2.4, baseline: ...}}. Best-effort — this is enrichment, so a failure to
     * compute it silently omits the section rather than costing the user their turn.
     */
    private void appendInsights(StringBuilder sb, User user, List<Budget> budgets,
                                List<Transaction> history, LocalDate asOf) {
        if (history.isEmpty()) return;
        List<Insight> insights;
        try {
            insights = new ArrayList<>(insightAnalyzer.analyze(history, budgets, asOf));
            // Income lives on the User, which the pure analyzer deliberately doesn't load — the
            // pull use case adds this finding the same way.
            insightAnalyzer.detectBudgetOverIncome(budgets, user == null ? null : user.getIncome(),
                            YearMonth.from(asOf))
                    .ifPresent(insights::add);
        } catch (Exception e) {
            log.warn("UserFinancialDataService: could not compute insights: {}", e.getMessage());
            return;
        }
        if (insights.isEmpty()) return;

        insights.sort(Comparator.comparingInt((Insight i) -> i.getSeverity().ordinal()).reversed());

        sb.append("\n=== D. Phân tích chi tiêu tự động (cùng nguồn với màn hình Phân tích) ===\n");
        insights.stream().limit(MAX_INSIGHTS_LISTED).forEach(i -> {
            String line = describeInsight(i);
            if (line != null) sb.append("  - ").append(line).append('\n');
        });
        if (insights.size() > MAX_INSIGHTS_LISTED) {
            sb.append("  (còn ").append(insights.size() - MAX_INSIGHTS_LISTED)
                    .append(" phát hiện khác mức độ thấp hơn, không hiển thị ở đây)\n");
        }
        sb.append("  Khi trả lời về xu hướng, dự báo cuối tháng hay mức chi bất thường, hãy TRÍCH\n")
                .append("  các phát hiện này thay vì tự suy luận từ danh sách giao dịch.\n");
    }

    /**
     * One finding → one Vietnamese sentence, with its severity in front.
     *
     * <p>Only the types {@code analyze()} plus {@code detectBudgetOverIncome} can actually produce
     * are spelled out; the notification-only types (large transaction, duplicate charge, month-end
     * recap findings, project health) never arrive on this path, so they fall through to a generic
     * line rather than being given wording that would never be exercised.
     */
    private String describeInsight(Insight insight) {
        Map<String, Object> m = insight.getMetrics();
        String severity = switch (insight.getSeverity()) {
            case CRITICAL -> "[Nghiêm trọng]";
            case WARN -> "[Cảnh báo]";
            case INFO -> "[Thông tin]";
        };
        String category = insight.getCategory() == null ? null
                : FinancialTermFormatter.category(insight.getCategory(), false);

        String body = switch (insight.getType()) {
            case OVERSPEND_ANOMALY -> String.format(
                    "Danh mục %s đang chi bất thường: tháng này %,.0f VND so với mức trung bình %,.0f VND "
                            + "của %s tháng gần đây.",
                    category, num(m, "actual"), num(m, "baseline"), text(m, "sampleMonths"));
            case BUDGET_PACE -> String.format(
                    "Danh mục %s theo đà hiện tại sẽ chi khoảng %,.0f VND vào cuối tháng, "
                            + "vượt hạn mức %,.0f VND (hiện đã chi %,.0f VND sau %s/%s ngày).",
                    category, num(m, "projectedEndOfMonth"), num(m, "limit"), num(m, "spent"),
                    text(m, "dayOfMonth"), text(m, "daysInMonth"));
            case BUDGET_UNDERSPEND -> String.format(
                    "Danh mục %s đang được cấp dư: hạn mức %,.0f VND nhưng thường chỉ chi khoảng "
                            + "%,.0f VND, dự kiến cuối tháng khoảng %,.0f VND — có thể cắt bớt.",
                    category, num(m, "limit"), num(m, "medianActual"), num(m, "projectedEndOfMonth"));
            case CATEGORY_TREND -> String.format(
                    "Danh mục %s có xu hướng tăng đều khoảng %.0f%%/tháng, trung bình %,.0f VND/tháng "
                            + "trong %s tháng gần đây.",
                    category, pct(m, "growthRatePerMonth"), num(m, "meanMonthly"), text(m, "sampleMonths"));
            case RECURRING -> String.format(
                    "Khoản chi lặp lại hàng tháng \"%s\" thuộc danh mục %s, khoảng %,.0f VND/tháng, "
                            + "đã lặp %s tháng.",
                    text(m, "description"), category, num(m, "monthlyAmount"), text(m, "monthsObserved"));
            case SUBSCRIPTION_PRICE_HIKE -> String.format(
                    "Khoản chi định kỳ \"%s\" vừa TĂNG GIÁ: từ khoảng %,.0f VND lên %,.0f VND (tăng %.0f%%).",
                    text(m, "description"), num(m, "previousAmount"), num(m, "newAmount"),
                    pct(m, "increaseRatio"));
            case SAVINGS_CAPACITY -> String.format(
                    "Ước tính còn khoảng %,.0f VND/tháng có thể để dành, tính từ thu nhập trung bình "
                            + "%,.0f VND trừ chi phí cố định %,.0f VND và ngân sách đã cam kết %,.0f VND.",
                    num(m, "disposable"), num(m, "avgMonthlyIncome"), num(m, "recurringCosts"),
                    num(m, "committedBudgets"));
            case BUDGET_OVER_INCOME -> String.format(
                    "Tổng hạn mức các ngân sách tháng (%,.0f VND) đang VƯỢT thu nhập theo hồ sơ "
                            + "(%,.0f VND), vượt %,.0f VND.",
                    num(m, "committedBudgets"), num(m, "income"), num(m, "overBy"));
            default -> null;
        };
        return body == null ? null : severity + " " + body;
    }

    private BigDecimal num(Map<String, Object> metrics, String key) {
        Object v = metrics.get(key);
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    /** A ratio metric ({@code 0.25}) rendered as a percentage number ({@code 25}). */
    private double pct(Map<String, Object> metrics, String key) {
        return num(metrics, key).doubleValue() * 100d;
    }

    private String text(Map<String, Object> metrics, String key) {
        Object v = metrics.get(key);
        return v == null ? "?" : String.valueOf(v);
    }

    /**
     * Lists the groups this user belongs to, with the ids and states every group operation needs.
     *
     * <p>Without this block the assistant could only ever describe the Group screen, because it
     * had no groupId to put in a draft and no way to know whether the user administers the group
     * or whether it is already LOCKED — the two facts that decide which operations are even
     * possible. {@code isAdmin} and {@code status} are what let it answer "you need to lock the
     * group first" instead of proposing an operation that would be rejected at confirm time.
     */
    private void appendGroups(StringBuilder sb, UUID userId) {
        List<Group> groups;
        try {
            groups = groupRepository.findAllByMember(UserId.of(userId.toString())).stream()
                    .filter(g -> g.getStatus() != GroupStatus.DISSOLVED)
                    .limit(MAX_GROUPS_LISTED)
                    .toList();
        } catch (Exception e) {
            // The group tables are not essential to a financial answer — a lookup failure must
            // not cost the user their whole turn, so the block is simply omitted.
            log.warn("UserFinancialDataService: could not load groups for user {}: {}",
                    userId, e.getMessage());
            return;
        }
        if (groups.isEmpty()) return;

        sb.append("\n=== H. Nhóm ===\n");
        for (Group g : groups) {
            GroupProject gp = groupProjectRepository.findByGroupId(g.getId().value()).orElse(null);
            sb.append(String.format(
                    "  %s: groupId=%s status=%s isAdmin=%s joinedMembers=%d groupProjectId=%s groupProjectStatus=%s\n",
                    g.getName(),
                    g.getId().value(),
                    g.getStatus(),
                    g.getAdminId().equals(UserId.of(userId.toString())),
                    g.joinedMembers().size(),
                    gp == null ? "none" : gp.getId().value(),
                    gp == null ? "none" : gp.getStatus()));
        }
    }

    /** How many individual transaction rows {@link #buildChatContext} lists. Enough to answer
     *  "what did I spend on recently?" and to give the assistant a txId to edit/delete, without
     *  letting a busy month push the budget/project sections out of the prompt. */
    private static final int RECENT_TRANSACTION_LIMIT = 15;

    private static final DateTimeFormatter TRANSACTION_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** System-generated rows the chat must not offer to edit or delete — kept in lockstep with
     *  {@code TransactionChangeService}'s own refusal, which is the enforcing side. */
    private static final Set<Category> PROJECT_LEDGER_CATEGORIES =
            EnumSet.of(Category.PROJECT_CONTRIBUTION, Category.PROJECT_REFUND);

    private void appendTransactions(StringBuilder sb, List<Transaction> txs, int month, int year) {
        if (txs.isEmpty()) {
            sb.append("\nKhông có giao dịch nào trong tháng ").append(month).append("/").append(year).append(".\n");
            return;
        }

        List<Transaction> recent = txs.stream()
                .sorted(Comparator.comparing(Transaction::getDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(RECENT_TRANSACTION_LIMIT)
                .toList();

        sb.append("\n=== F. Giao dịch tháng ").append(month).append("/").append(year)
                .append(" (").append(recent.size()).append("/").append(txs.size())
                .append(" giao dịch gần nhất) ===\n");
        recent.forEach(t -> {
            boolean locked = t.getProjectId() != null
                    || (t.getCategory() != null && PROJECT_LEDGER_CATEGORIES.contains(t.getCategory()));
            // Category carries its plain label alongside the enum name for the same reason the
            // budget rows do: the enum is what a draft must be filled with, the label is what the
            // reply is allowed to say.
            sb.append(String.format(
                    "  txId=%s date=%s type=%s category=%s (%s) amount=%,.0f VND desc=%s locked=%s\n",
                    t.getId(),
                    t.getDate() != null ? TRANSACTION_DATE_FORMAT.format(t.getDate()) : "?",
                    t.getType(),
                    t.getCategory(),
                    FinancialTermFormatter.category(t.getCategory(), false),
                    t.getAmount() != null && t.getAmount().getValue() != null
                            ? t.getAmount().getValue() : BigDecimal.ZERO,
                    (t.getDescription() == null || t.getDescription().isBlank())
                            ? "(không có)" : "\"" + t.getDescription() + "\"",
                    locked));
        });
        if (txs.size() > recent.size()) {
            sb.append("  (còn ").append(txs.size() - recent.size())
                    .append(" giao dịch khác trong tháng không hiển thị ở đây)\n");
        }
    }
}