package com.example.smartmoney.application.services;

import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * UC1 — Budget Adjustment:
 * Analyses the user's current budgets and computes actionable reduction suggestions.
 *
 * This service is PURELY READ-ONLY — it never writes or mutates any budget record.
 * The frontend receives a {@link ChatResponse.BudgetUpdateSuggestion} and calls
 * {@code PUT /api/v1/budgets/{id}} only after the user explicitly confirms.
 *
 * Suggestion algorithm:
 *  1. Identify the budget category most relevant to the user's message (keyword match).
 *  2. Fall back to the highest-limit budget if no category keyword is found.
 *  3. Suggest a 20% reduction (configurable via REDUCTION_RATIO).
 *  4. Return the freed amount so the frontend can ask "move to which fund?".
 */
@Service
@RequiredArgsConstructor
public class BudgetAdvisorService {

    private final BudgetRepository budgetRepository;

    // Suggest cutting the identified category by this fraction (20% reduction)
    private static final double REDUCTION_RATIO = 0.20;

    // Proactive near-limit nudge fires once a budget category crosses this % used
    private static final BigDecimal NEAR_LIMIT_THRESHOLD_PCT = BigDecimal.valueOf(80);

    /**
     * Returns a list of budget reduction suggestions based on the user's message.
     *
     * @param userId      the authenticated user
     * @param month       month being analysed (1-12)
     * @param year        year being analysed
     * @param messageLow  user message in lowercase, used for category keyword matching
     * @return list of suggestions — typically 1 item targeting the most relevant category
     */
    public List<ChatResponse.BudgetUpdateSuggestion> suggestReductions(
            UUID userId, int month, int year, String messageLow) {

        List<Budget> budgets = budgetRepository.findByUserIdMonthYear(userId, month, year);
        if (budgets.isEmpty()) {
            return Collections.emptyList();
        }

        BigDecimal totalBudget = budgets.stream()
                .map(Budget::getAmountLimit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Step 1: try to match a category the user explicitly named in their message
        // e.g. "tôi muốn cắt chi tiêu ăn uống" → matches FOOD via "ăn" keyword
        Optional<Budget> targetBudget = budgets.stream()
                .filter(b -> messageLow.contains(b.getCategory().name().toLowerCase())
                          || messageLow.contains(translateCategoryToVietnamese(b.getCategory().name())))
                .max(Comparator.comparing(Budget::getAmountLimit));

        // Step 2: no explicit category — fall back to the highest-limit budget
        // as the most impactful place to cut spending
        if (targetBudget.isEmpty()) {
            targetBudget = budgets.stream()
                    .max(Comparator.comparing(Budget::getAmountLimit));
        }

        if (targetBudget.isEmpty()) {
            return Collections.emptyList();
        }

        Budget b = targetBudget.get();
        BigDecimal current = b.getAmountLimit();

        // Step 3: compute suggested new limit = current * (1 - REDUCTION_RATIO)
        BigDecimal suggested = current
                .multiply(BigDecimal.valueOf(1.0 - REDUCTION_RATIO))
                .setScale(0, RoundingMode.HALF_UP);

        BigDecimal freed = current.subtract(suggested);

        // Percentage of total budget this category represents
        BigDecimal pct = totalBudget.compareTo(BigDecimal.ZERO) > 0
                ? current.multiply(BigDecimal.valueOf(100))
                         .divide(totalBudget, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // UC1: reason string displayed in the confirm card and given to the AI for context.
        // Matches the spec: "Food is 2.8M (35%). Cut to 2M, freeing 800K — move to which fund?"
        String reason = MessageLanguageDetector.isEnglish(messageLow)
                ? String.format(
                        "%s is currently %,.0f VND (%.1f%% of total budget). " +
                        "Cut it to %,.0f VND, freeing up %,.0f VND — which fund should it go to?",
                        b.getCategory().name(), current, pct, suggested, freed)
                : String.format(
                        "%s hiện là %,.0f VND (%.1f%% tổng ngân sách). " +
                        "Giảm xuống %,.0f VND, giải phóng %,.0f VND — bạn muốn chuyển khoản này sang quỹ nào?",
                        b.getCategory().name(), current, pct, suggested, freed);

        return List.of(ChatResponse.BudgetUpdateSuggestion.builder()
                .budgetId(b.getId().toString())
                .category(b.getCategory().name())
                .currentAmount(current.longValue())
                .suggestedAmount(suggested.longValue())
                .reason(reason)
                .build());
    }

    /**
     * Proactive safety-net check (not tied to any keyword match): flags the most
     * over-threshold budget category this month, if any has crossed
     * {@link #NEAR_LIMIT_THRESHOLD_PCT}% used. Used to volunteer a nudge on a turn where the
     * user didn't explicitly ask about their budget, mirroring the spec's
     * "Bạn đã dùng 85% budget ăn uống ... muốn điều chỉnh không?" example.
     *
     * @return empty when no budget is near/over its limit
     */
    public Optional<ChatResponse.BudgetUpdateSuggestion> checkNearLimit(
            UUID userId, int month, int year, int daysLeft, boolean english) {

        List<Budget> budgets = budgetRepository.findByUserIdMonthYear(userId, month, year);
        if (budgets.isEmpty()) return Optional.empty();

        Optional<Budget> worst = budgets.stream()
                .filter(b -> b.getAmountLimit().compareTo(BigDecimal.ZERO) > 0)
                .max(Comparator.comparing(b -> pctUsed(b)));

        if (worst.isEmpty()) return Optional.empty();

        Budget b = worst.get();
        BigDecimal pct = pctUsed(b);
        if (pct.compareTo(NEAR_LIMIT_THRESHOLD_PCT) < 0) return Optional.empty();

        BigDecimal current = b.getAmountLimit();
        BigDecimal suggested = current
                .multiply(BigDecimal.valueOf(1.0 - REDUCTION_RATIO))
                .setScale(0, RoundingMode.HALF_UP);

        String reason = english
                ? String.format(
                        "You've used %.0f%% of your %s budget with %d day(s) left this month — " +
                        "want to lower the limit to %,.0f VND?",
                        pct, b.getCategory().name(), daysLeft, suggested)
                : String.format(
                        "Bạn đã dùng %.0f%% ngân sách %s, còn %d ngày nữa trong tháng — " +
                        "bạn có muốn điều chỉnh xuống %,.0f VND không?",
                        pct, b.getCategory().name(), daysLeft, suggested);

        return Optional.of(ChatResponse.BudgetUpdateSuggestion.builder()
                .budgetId(b.getId().toString())
                .category(b.getCategory().name())
                .currentAmount(current.longValue())
                .suggestedAmount(suggested.longValue())
                .reason(reason)
                .build());
    }

    private BigDecimal pctUsed(Budget b) {
        BigDecimal spent = b.getSpent() != null ? b.getSpent() : BigDecimal.ZERO;
        return b.getAmountLimit().compareTo(BigDecimal.ZERO) > 0
                ? spent.multiply(BigDecimal.valueOf(100)).divide(b.getAmountLimit(), 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    /**
     * Builds a human-readable budget summary string for the AI prompt,
     * giving the LLM accurate numbers so it can explain suggestions naturally.
     */
    public String buildBudgetContext(UUID userId, int month, int year) {
        List<Budget> budgets = budgetRepository.findByUserIdMonthYear(userId, month, year);
        if (budgets.isEmpty()) {
            return "Người dùng chưa thiết lập ngân sách tháng " + month + "/" + year + ".\n";
        }

        BigDecimal total = budgets.stream()
                .map(Budget::getAmountLimit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        StringBuilder sb = new StringBuilder("Ngân sách tháng ").append(month).append("/").append(year).append(":\n");
        // Sort highest-limit first so the AI sees the most impactful categories first
        budgets.stream()
               .sorted(Comparator.comparing(Budget::getAmountLimit).reversed())
               .forEach(b -> {
                   BigDecimal spent = b.getSpent() != null ? b.getSpent() : BigDecimal.ZERO;
                   BigDecimal pct = total.compareTo(BigDecimal.ZERO) > 0
                           ? b.getAmountLimit().multiply(BigDecimal.valueOf(100))
                                    .divide(total, 1, RoundingMode.HALF_UP)
                           : BigDecimal.ZERO;
                   sb.append(String.format("  - %s: %,.0f VND (%.1f%%) — đã chi: %,.0f VND\n",
                           b.getCategory().name(),
                           b.getAmountLimit(),
                           pct,
                           spent));
               });
        sb.append(String.format("  Tổng ngân sách: %,.0f VND\n", total));
        return sb.toString();
    }

    // Maps English category enum names to common Vietnamese keywords so that
    // messages like "tôi muốn giảm chi tiêu ăn" can match the FOOD category
    private String translateCategoryToVietnamese(String categoryName) {
        return switch (categoryName.toUpperCase()) {
            case "FOOD"           -> "ăn";
            case "TRANSPORTATION" -> "đi lại";
            case "ENTERTAINMENT"  -> "giải trí";
            case "SHOPPING"       -> "mua sắm";
            case "HEALTH"         -> "y tế";
            case "EDUCATION"      -> "giáo dục";
            case "UTILITIES"      -> "tiện ích";
            case "CLOTHING"       -> "quần áo";
            default               -> categoryName.toLowerCase();
        };
    }
}
