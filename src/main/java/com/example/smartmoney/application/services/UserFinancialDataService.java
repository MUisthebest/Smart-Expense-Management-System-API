package com.example.smartmoney.application.services;


import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.GroupProject;
import com.example.smartmoney.domain.models.group.enums.GroupStatus;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.GroupRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

        if (!transactions.isEmpty()) {
            double totalIncome = transactions.stream()
                    .filter(t -> t.getType() == TransactionType.INCOME)
                    .mapToDouble(t -> t.getAmount().getValue().doubleValue())
                    .sum();
            double totalExpense = transactions.stream()
                    .filter(t -> t.getType() == TransactionType.EXPENSE)
                    .mapToDouble(t -> t.getAmount().getValue().doubleValue())
                    .sum();
            sb.append("- Tổng thu nhập tháng này: ").append(totalIncome).append("\n");
            sb.append("- Tổng chi tiêu tháng này: ").append(totalExpense).append("\n");

            var categoryExpenses = transactions.stream()
                    .filter(t -> t.getType() == TransactionType.EXPENSE)
                    .collect(Collectors.groupingBy(
                            t -> t.getCategory().name(),
                            Collectors.summingDouble(t -> t.getAmount().getValue().doubleValue())
                    ));
            sb.append("- Chi tiêu theo danh mục (top 5):\n");
            categoryExpenses.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(5)
                    .forEach(e -> sb.append("  * ").append(e.getKey()).append(": ").append(e.getValue()).append("\n"));
        } else if (dataTypes.contains(DataType.TRANSACTIONS)) {
            sb.append("- Không có giao dịch trong tháng này.\n");
        }

        if (!projects.isEmpty()) {
            sb.append("- Các dự án đang hoạt động:\n");
            projects.stream().limit(5).forEach(p -> {
                        Money target = p.getTargetAmount();
                        Money contributed = p.totalContributed();
                        Money remaining = p.remaining();

                        BigDecimal progress = p.progressPercent();

                        long monthsLeft = ChronoUnit.MONTHS.between(
                                LocalDate.now(),
                                p.getDeadline()
                        );

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
            sb.append("- Không có dự án nào.\n");
        }

        return sb.toString();
    }

    /** Income, safe-spending headroom, and days-left for a month — the numeric values behind
     *  {@link #buildChatContext}'s prompt text, exposed so callers can run proactive checks
     *  (near-limit, feasibility, month-end leftover, surplus trend) without re-deriving them. */
    public record FinancialSnapshot(BigDecimal monthlyIncome, BigDecimal safeSpending, int daysLeft) {}

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
        List<Budget> budgets = budgetRepository.findByUserIdMonthYear(userId, month, year);
        BigDecimal totalBudgetLimit = budgets.stream()
                .map(Budget::getAmountLimit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalBudgetSpent = budgets.stream()
                .map(b -> b.getSpent() != null ? b.getSpent() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Project> projects = projectRepository.findAllByUser(UserId.of(userId.toString()));
        LocalDate today = LocalDate.now();

        User user = userRepository.findById(userId).orElse(null);
        BigDecimal monthlyIncome = user != null && user.getIncome() != null ? user.getIncome() : BigDecimal.ZERO;

        // safe_spending = income - active projects' monthly saving commitments, when the user
        // has completed financial onboarding; otherwise fall back to remaining budget headroom
        // this month so the prompt still has a sensible number.
        BigDecimal safeSpending;
        if (user != null && user.hasCompletedFinancialSetup()) {
            BigDecimal activeProjectMonthlyCommitment = projects.stream()
                    .filter(p -> p.getDeadline().isAfter(today) && p.getTracking() != null)
                    .map(p -> p.getTracking().getMonthlySaving())
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            safeSpending = user.calculateSafeSpending(activeProjectMonthlyCommitment);
        } else {
            safeSpending = totalBudgetLimit.subtract(totalBudgetSpent).max(BigDecimal.ZERO);
        }

        int daysLeft = today.lengthOfMonth() - today.getDayOfMonth();
        return new FinancialSnapshot(monthlyIncome, safeSpending, daysLeft);
    }

    /**
     * Builds the full financial context block for the Gemini chat prompt.
     * Covers: income (from INCOME transactions), safe_spending (budget remaining),
     * budgets (with budgetId/category/spent/limit), and projects (with
     * monthlySaving/moneyOwed/monthsLeft) for the given month/year.
     */
    public String buildChatContext(UUID userId, int month, int year) {
        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
        LocalDateTime start = monthStart.atStartOfDay();
        LocalDateTime end = monthEnd.atTime(23, 59, 59);

        // Income from INCOME-type transactions this month
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

        // Budgets for the month
        List<Budget> budgets = budgetRepository.findByUserIdMonthYear(userId, month, year);

        // Projects
        List<Project> projects = projectRepository.findAllByUser(UserId.of(userId.toString()));
        LocalDate today = LocalDate.now();

        FinancialSnapshot snapshot = getFinancialSnapshot(userId, month, year);
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
        sb.append("=== Tình hình dòng tiền tháng ").append(month).append("/").append(year).append(" ===\n");
        sb.append(String.format("Thu nhập tháng %d/%d (đã ghi nhận qua giao dịch): %,.0f VND\n",
                month, year, totalIncome));
        sb.append(String.format("Thu nhập hàng tháng (theo thiết lập): %,.0f VND\n", monthlyIncome));
        sb.append(String.format("Chi tiêu thực tế tháng này: %,.0f VND\n", totalExpense));
        sb.append(String.format("Chênh lệch (thu nhập tháng - chi tiêu thực tế): %,.0f VND\n", cashFlowDelta));
        sb.append("Trạng thái dòng tiền tháng: ").append(cashFlowStatus).append('\n');

        // ── Kế hoạch tiết kiệm (safe_spending) ────────────────────────────────
        // Explicitly relabelled and formula'd so the LLM never reads safe_spending as
        // "cash left this month" — it is planned monthly headroom, always non-negative,
        // does NOT decrement with actual expenses.
        sb.append("\n=== Kế hoạch tiết kiệm ===\n");
        sb.append(String.format(
                "Chi tiêu an toàn hàng tháng theo kế hoạch (safe_spending): %,.0f VND\n", safeSpending));
        sb.append("  Công thức: MAX(0, Thu nhập hàng tháng - Σ monthlySaving các dự án ACTIVE).\n");
        sb.append("  Đây là hạn mức TÍNH THEO KẾ HOẠCH, KHÔNG trừ chi tiêu thực tế đã phát sinh.\n");
        sb.append(String.format("Today: %s, Days left: %d\n", today, daysLeft));

        // ── Ngân sách tháng ───────────────────────────────────────────────────
        // status is alertLevel (SAFE/CAUTION/WARNING/EXCEEDED) — surfaced so the AI never has
        // to compute the pressure level itself.
        if (!budgets.isEmpty()) {
            sb.append("\n=== Ngân sách tháng ").append(month).append("/").append(year).append(" ===\n");
            budgets.forEach(b -> {
                BigDecimal spent = b.getSpent() != null ? b.getSpent() : BigDecimal.ZERO;
                BigDecimal pct = b.getAmountLimit().compareTo(BigDecimal.ZERO) > 0
                        ? spent.multiply(BigDecimal.valueOf(100))
                        .divide(b.getAmountLimit(), 1, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                sb.append(String.format("  budgetId=%s category=%s spent=%,.0f limit=%,.0f (%.1f%%) status=%s\n",
                        b.getId(), b.getCategory().name(), spent, b.getAmountLimit(), pct, b.getAlertLevel(spent)));
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
        if (!projects.isEmpty()) {
            sb.append("\n=== Dự án ===\n");
            projects.stream()
                    .sorted(Comparator.comparingInt(p -> p.getStatus() == ProjectStatus.ACTIVE ? 0 : 1))
                    .limit(5)
                    .forEach(p -> {
                        long monthsLeft = ChronoUnit.MONTHS.between(today, p.getDeadline());
                        ProjectTracking tracking = p.getTracking();
                        BigDecimal monthlySaving = tracking != null ? tracking.getMonthlySaving() : BigDecimal.ZERO;
                        BigDecimal moneyOwed = tracking != null ? tracking.getMoneyOwed() : BigDecimal.ZERO;
                        // projectId is listed so a draft-driven edit/delete can name the exact row
                        // instead of relying on a project-name match, which is ambiguous the
                        // moment two projects share a name.
                        sb.append(String.format(
                                "  %s: projectId=%s status=%s priority=%s target=%s remaining=%s monthlySaving=%,.0f moneyOwed=%,.0f monthsLeft=%d linkedToGroup=%s\n",
                                p.getName(),
                                p.getId(),
                                p.getStatus(),
                                p.getPriority(),
                                p.getTargetAmount(),
                                p.remaining(),
                                monthlySaving,
                                moneyOwed,
                                monthsLeft,
                                p.isSubPersonal()));
                    });
        }

        appendGroups(sb, userId);

        return sb.toString();
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

        sb.append("\n=== Nhóm ===\n");
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

        sb.append("\n=== Giao dịch tháng ").append(month).append("/").append(year)
                .append(" (").append(recent.size()).append("/").append(txs.size())
                .append(" giao dịch gần nhất) ===\n");
        recent.forEach(t -> {
            boolean locked = t.getProjectId() != null
                    || (t.getCategory() != null && PROJECT_LEDGER_CATEGORIES.contains(t.getCategory()));
            sb.append(String.format("  txId=%s date=%s type=%s category=%s amount=%,.0f VND desc=%s locked=%s\n",
                    t.getId(),
                    t.getDate() != null ? TRANSACTION_DATE_FORMAT.format(t.getDate()) : "?",
                    t.getType(),
                    t.getCategory(),
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