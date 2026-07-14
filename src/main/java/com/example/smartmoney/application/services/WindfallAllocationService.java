package com.example.smartmoney.application.services;

import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Computes structured allocation payloads for windfall income and large unplanned expenses.
 *
 * INCOME_WINDFALL: allocates a portion to savings projects (PUT .../tracking {moneySaved})
 *   and the rest to budget limits (PUT /budgets/{id} {amountLimit}).
 *
 * LARGE_EXPENSE: identifies which budget to draw from (increase limit) and, if the
 *   budget is insufficient, which project deadlines to extend to free monthly cash.
 *
 * All suggestions are read-only computations — this service never writes any data.
 */
@Service
@RequiredArgsConstructor
public class WindfallAllocationService {

    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "([\\d][\\d,.]*)\\s*(tỷ|ty|billion|triệu|tr|M|m|K|k|nghìn|ngàn)?",
            Pattern.UNICODE_CASE);

    private static final BigDecimal DEFAULT_AMOUNT = BigDecimal.valueOf(5_000_000);
    private static final double SAVINGS_RATIO  = 0.60;  // 60% windfall → projects
    private static final double BUDGET_RATIO   = 0.40;  // 40% windfall → budget boosts
    private static final int    DEFER_MONTHS   = 2;     // months to extend for large expense

    private final BudgetRepository budgetRepository;
    private final ProjectRepository projectRepository;

    // ── INCOME_WINDFALL ───────────────────────────────────────────────────────

    /**
     * Computes project savings boosts for a windfall income event.
     * 60% of the windfall is split across HIGH-priority active projects first,
     * then MEDIUM, then LOW — greedy fill until the pool is exhausted.
     */
    public List<ChatResponse.SavingsPlanSuggestion> suggestSavingsBoost(
            UUID userId, int month, int year, String messageLow) {

        BigDecimal windfall = parseAmount(messageLow);
        if (windfall.compareTo(BigDecimal.ZERO) == 0) windfall = DEFAULT_AMOUNT;

        BigDecimal savingsPool = windfall.multiply(BigDecimal.valueOf(SAVINGS_RATIO))
                .setScale(0, RoundingMode.HALF_UP);

        List<Project> projects = activeProjects(userId);
        if (projects.isEmpty()) return Collections.emptyList();

        List<ChatResponse.SavingsPlanSuggestion> result = new ArrayList<>();
        BigDecimal remaining = savingsPool;

        for (Project p : projects) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal projectNeed = p.remaining().amount();
            if (projectNeed.compareTo(BigDecimal.ZERO) <= 0) continue;

            ProjectTracking tracking = p.getTracking();
            BigDecimal currentSaved = tracking != null && tracking.getMoneySaved() != null
                    ? tracking.getMoneySaved() : BigDecimal.ZERO;

            // Allocate at most what the project needs, cap at remaining pool
            BigDecimal allocate = remaining.min(projectNeed).setScale(0, RoundingMode.HALF_UP);
            remaining = remaining.subtract(allocate);

            boolean english = MessageLanguageDetector.isEnglish(messageLow);
            result.add(ChatResponse.SavingsPlanSuggestion.builder()
                    .projectId(p.getId().toString())
                    .projectName(p.getName())
                    .currentMoneySaved(currentSaved.longValue())
                    .suggestedAddAmount(allocate.longValue())
                    .newMoneySaved(currentSaved.add(allocate).longValue())
                    .suggestedMonthLeft(null)
                    .reason(english
                            ? String.format(
                                    "Add %,.0f VND to '%s' from the windfall income — the project finishes sooner.",
                                    allocate, p.getName())
                            : String.format(
                                    "Thêm %,.0f VND vào '%s' từ nguồn thu đột xuất — dự án hoàn thành nhanh hơn.",
                                    allocate, p.getName()))
                    .build());
        }
        return result;
    }

    /**
     * Computes budget limit increases for a windfall income event.
     * 40% of the windfall is spread across top budget categories proportionally by limit size.
     */
    public List<ChatResponse.BudgetUpdateSuggestion> suggestBudgetBoosts(
            UUID userId, int month, int year, String messageLow) {

        BigDecimal windfall = parseAmount(messageLow);
        if (windfall.compareTo(BigDecimal.ZERO) == 0) windfall = DEFAULT_AMOUNT;

        BigDecimal budgetPool = windfall.multiply(BigDecimal.valueOf(BUDGET_RATIO))
                .setScale(0, RoundingMode.HALF_UP);

        List<Budget> budgets = budgetRepository.findByUserIdMonthYear(userId, month, year);
        if (budgets.isEmpty()) return Collections.emptyList();

        BigDecimal totalLimit = budgets.stream()
                .map(Budget::getAmountLimit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalLimit.compareTo(BigDecimal.ZERO) == 0) return Collections.emptyList();

        // Top 3 budgets by limit — give them a proportional slice of the pool
        return budgets.stream()
                .sorted(Comparator.comparing(Budget::getAmountLimit).reversed())
                .limit(3)
                .map(b -> {
                    BigDecimal share = budgetPool.multiply(b.getAmountLimit())
                            .divide(totalLimit, 0, RoundingMode.HALF_UP);
                    BigDecimal newLimit = b.getAmountLimit().add(share);
                    boolean english = MessageLanguageDetector.isEnglish(messageLow);
                    return ChatResponse.BudgetUpdateSuggestion.builder()
                            .budgetId(b.getId().toString())
                            .category(b.getCategory().name())
                            .currentAmount(b.getAmountLimit().longValue())
                            .suggestedAmount(newLimit.longValue())
                            .reason(english
                                    ? String.format(
                                            "Increase %s budget by %,.0f VND (%.0f%% of the windfall) -> new limit: %,.0f VND.",
                                            b.getCategory().name(), share, BUDGET_RATIO * 100, newLimit)
                                    : String.format(
                                            "Tăng ngân sách %s thêm %,.0f VND (%.0f%% khoản thu đột xuất) → giới hạn mới: %,.0f VND.",
                                            b.getCategory().name(), share,
                                            BUDGET_RATIO * 100, newLimit))
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ── LARGE_EXPENSE ─────────────────────────────────────────────────────────

    /**
     * Suggests which budget to draw from to cover a large expense.
     * Targets the budget with the most headroom (limit − spent).
     * If no single budget can cover the expense, returns multiple entries splitting the cost.
     */
    public List<ChatResponse.BudgetUpdateSuggestion> suggestExpenseCoverage(
            UUID userId, int month, int year, String messageLow) {

        BigDecimal expense = parseAmount(messageLow);
        if (expense.compareTo(BigDecimal.ZERO) == 0) expense = DEFAULT_AMOUNT;

        List<Budget> budgets = budgetRepository.findByUserIdMonthYear(userId, month, year);
        if (budgets.isEmpty()) return Collections.emptyList();

        // Sort by headroom descending
        List<Budget> byHeadroom = budgets.stream()
                .sorted(Comparator.comparing((Budget b) -> {
                    BigDecimal spent = b.getSpent() != null ? b.getSpent() : BigDecimal.ZERO;
                    return b.getAmountLimit().subtract(spent);
                }).reversed())
                .collect(Collectors.toList());

        List<ChatResponse.BudgetUpdateSuggestion> result = new ArrayList<>();
        BigDecimal remaining = expense;
        boolean english = MessageLanguageDetector.isEnglish(messageLow);

        for (Budget b : byHeadroom) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal spent = b.getSpent() != null ? b.getSpent() : BigDecimal.ZERO;
            BigDecimal headroom = b.getAmountLimit().subtract(spent).max(BigDecimal.ZERO);

            // How much more limit to add so this budget can absorb its share
            BigDecimal cover = remaining.min(headroom.compareTo(BigDecimal.ZERO) > 0
                    ? headroom : remaining);
            // If headroom is already enough, don't increase the limit — just flag it
            if (headroom.compareTo(remaining) >= 0) {
                result.add(ChatResponse.BudgetUpdateSuggestion.builder()
                        .budgetId(b.getId().toString())
                        .category(b.getCategory().name())
                        .currentAmount(b.getAmountLimit().longValue())
                        .suggestedAmount(b.getAmountLimit().longValue()) // no change needed
                        .reason(english
                                ? String.format(
                                        "%s budget has %,.0f VND headroom left — enough to cover the %,.0f VND unplanned expense.",
                                        b.getCategory().name(), headroom, expense)
                                : String.format(
                                        "Ngân sách %s còn dư %,.0f VND — đủ để trang trải chi phí đột xuất %,.0f VND.",
                                        b.getCategory().name(), headroom, expense))
                        .build());
                remaining = BigDecimal.ZERO;
            } else {
                // Need to increase the limit
                BigDecimal needed = remaining.subtract(headroom).max(BigDecimal.ZERO);
                BigDecimal newLimit = b.getAmountLimit().add(needed);
                result.add(ChatResponse.BudgetUpdateSuggestion.builder()
                        .budgetId(b.getId().toString())
                        .category(b.getCategory().name())
                        .currentAmount(b.getAmountLimit().longValue())
                        .suggestedAmount(newLimit.longValue())
                        .reason(english
                                ? String.format(
                                        "Increase %s limit by %,.0f VND to cover the unplanned expense.",
                                        b.getCategory().name(), needed)
                                : String.format(
                                        "Tăng hạn mức %s thêm %,.0f VND để trang trải chi phí đột xuất.",
                                        b.getCategory().name(), needed))
                        .build());
                remaining = remaining.subtract(headroom.add(needed));
            }
        }
        return result;
    }

    /**
     * Suggests extending project deadlines to reduce monthly savings pressure
     * when a large expense leaves insufficient cash flow.
     */
    public List<ChatResponse.SavingsPlanSuggestion> suggestProjectDeferral(
            UUID userId, int month, int year, String messageLow) {
        return suggestProjectDeferral(userId, month, year, messageLow, BigDecimal.ZERO);
    }

    /**
     * Same as {@link #suggestProjectDeferral(UUID, int, int, String)} but treats
     * {@code extraCoverage} (e.g. idle funds freed from an abandoned project via
     * {@link #suggestAbandonedProjectFunds}) as additional headroom, so an ACTIVE project is only
     * deferred for whatever shortfall remains after that idle cash is counted.
     */
    public List<ChatResponse.SavingsPlanSuggestion> suggestProjectDeferral(
            UUID userId, int month, int year, String messageLow, BigDecimal extraCoverage) {

        BigDecimal expense = parseAmount(messageLow);
        if (expense.compareTo(BigDecimal.ZERO) == 0) expense = DEFAULT_AMOUNT;

        List<Project> projects = activeProjects(userId);
        if (projects.isEmpty()) return Collections.emptyList();

        // Check if total budget headroom + any idle abandoned-project funds already covers the
        // expense — if yes, no deferral of an ACTIVE project is needed.
        List<Budget> budgets = budgetRepository.findByUserIdMonthYear(userId, month, year);
        BigDecimal totalHeadroom = budgets.stream()
                .map(b -> b.getAmountLimit().subtract(b.getSpent() != null ? b.getSpent() : BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(extraCoverage != null ? extraCoverage : BigDecimal.ZERO);

        if (totalHeadroom.compareTo(expense) >= 0) return Collections.emptyList();

        // Suggest deferring LOW then MEDIUM priority projects by DEFER_MONTHS
        List<ChatResponse.SavingsPlanSuggestion> result = new ArrayList<>();
        BigDecimal shortfall = expense.subtract(totalHeadroom.max(BigDecimal.ZERO));

        for (Project p : projects) {
            if (shortfall.compareTo(BigDecimal.ZERO) <= 0) break;
            ProjectTracking tracking = p.getTracking();
            if (tracking == null) continue;

            BigDecimal monthlySaving = tracking.getMonthlySaving() != null
                    ? tracking.getMonthlySaving() : BigDecimal.ZERO;
            if (monthlySaving.compareTo(BigDecimal.ZERO) == 0) continue;

            int newMonthLeft = tracking.getMonthLeft() + DEFER_MONTHS;
            BigDecimal freed = monthlySaving.multiply(BigDecimal.valueOf(DEFER_MONTHS));
            shortfall = shortfall.subtract(freed);

            boolean english = MessageLanguageDetector.isEnglish(messageLow);
            result.add(ChatResponse.SavingsPlanSuggestion.builder()
                    .projectId(p.getId().toString())
                    .projectName(p.getName())
                    .currentMoneySaved(tracking.getMoneySaved() != null
                            ? tracking.getMoneySaved().longValue() : 0L)
                    .suggestedAddAmount(null)
                    .newMoneySaved(null)
                    .suggestedMonthLeft(newMonthLeft)
                    .reason(english
                            ? String.format(
                                    "Defer project '%s' by %d months -> frees up %,.0f VND/month to cover the unplanned expense.",
                                    p.getName(), DEFER_MONTHS, monthlySaving)
                            : String.format(
                                    "Hoãn dự án '%s' thêm %d tháng → giải phóng %,.0f VND/tháng để trang trải chi phí đột xuất.",
                                    p.getName(), DEFER_MONTHS, monthlySaving))
                    .build());
        }
        return result;
    }

    /**
     * Suggests using money already saved in an ABANDONED project to cover a large expense instead
     * of leaving it idle. An abandoned project no longer accrues monthly saving and can't take new
     * contributions ({@link Project#contribute} rejects it), but its tracked {@code moneySaved} is a
     * real lump sum sitting unused — a better source for a one-off expense than deferring an ACTIVE
     * project's future savings. (A hard-deleted project has nothing left to suggest — its tracking
     * row is gone — so this only ever looks at ABANDONED, not DELETE.)
     */
    public List<ChatResponse.SavingsPlanSuggestion> suggestAbandonedProjectFunds(
            UUID userId, String messageLow) {

        List<Project> abandoned = projectRepository.findAllByUser(UserId.of(userId.toString()))
                .stream()
                .filter(p -> p.getStatus() == ProjectStatus.ABANDONED)
                .filter(p -> {
                    ProjectTracking t = p.getTracking();
                    return t != null && t.getMoneySaved() != null
                            && t.getMoneySaved().compareTo(BigDecimal.ZERO) > 0;
                })
                .collect(Collectors.toList());
        if (abandoned.isEmpty()) return Collections.emptyList();

        boolean english = MessageLanguageDetector.isEnglish(messageLow);
        return abandoned.stream()
                .map(p -> {
                    BigDecimal saved = p.getTracking().getMoneySaved();
                    return ChatResponse.SavingsPlanSuggestion.builder()
                            .projectId(p.getId().toString())
                            .projectName(p.getName())
                            .currentMoneySaved(saved.longValue())
                            .suggestedAddAmount(saved.negate().longValue()) // negative = withdrawal
                            .newMoneySaved(0L)
                            .suggestedMonthLeft(null)
                            .reason(english
                                    ? String.format(
                                            "Project '%s' is abandoned but still has %,.0f VND saved — use it to cover the expense instead of leaving it idle.",
                                            p.getName(), saved)
                                    : String.format(
                                            "Dự án '%s' đã bị hủy nhưng vẫn còn %,.0f VND đã tiết kiệm — dùng số tiền này để trang trải chi phí thay vì để không.",
                                            p.getName(), saved))
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Active projects (status ACTIVE — excludes COMPLETED/ABANDONED/FROZEN/EXPIRED/CANCELLED,
     *  future deadline), highest priority first (HIGH &gt; MEDIUM &gt; LOW). */
    private List<Project> activeProjects(UUID userId) {
        LocalDate today = LocalDate.now();
        return projectRepository.findAllByUser(UserId.of(userId.toString()))
                .stream()
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE
                        && p.getDeadline().isAfter(today)
                        && ChronoUnit.MONTHS.between(today, p.getDeadline()) > 0)
                .sorted(Comparator.comparingInt(p -> -p.getPriority().ordinal())) // HIGH first
                .collect(Collectors.toList());
    }

    public BigDecimal parseAmount(String text) {
        Matcher m = AMOUNT_PATTERN.matcher(text);
        while (m.find()) {
            String numStr = m.group(1).replace(",", "");
            String unit = m.group(2);
            try {
                BigDecimal num = new BigDecimal(numStr);
                if (unit != null) {
                    return switch (unit.toLowerCase()) {
                        case "tỷ", "ty", "billion" -> num.multiply(BigDecimal.valueOf(1_000_000_000));
                        case "triệu", "tr", "m" -> num.multiply(BigDecimal.valueOf(1_000_000));
                        case "k", "nghìn", "ngàn" -> num.multiply(BigDecimal.valueOf(1_000));
                        default -> num;
                    };
                }
                if (num.compareTo(BigDecimal.valueOf(1_000)) >= 0) return num;
            } catch (NumberFormatException ignored) {}
        }
        return BigDecimal.ZERO;
    }
}
