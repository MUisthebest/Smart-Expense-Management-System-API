package com.example.smartmoney.application.services;

import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    // ── Budget category increase → suggest a swap instead ───────────────────

    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "([\\d][\\d,.]*)\\s*(tỷ|ty|billion|triệu|tr|M|m|K|k|nghìn|ngàn)?",
            Pattern.UNICODE_CASE);
    private static final BigDecimal DEFAULT_INCREASE_AMOUNT = BigDecimal.valueOf(500_000);

    /**
     * When the user asks to raise the limit on 1-2 categories, don't approve the increase
     * outright — suggest reducing the 1-2 categories with the LOWEST historical spending this
     * month by the same amount instead, keeping the total budget neutral. Returns the increase
     * entries followed by the offsetting decrease entries; empty when either side can't be
     * determined (no category keyword matched, or no other category exists to offset from) so a
     * non-neutral suggestion is never proposed.
     */
    public List<ChatResponse.BudgetUpdateSuggestion> suggestIncreaseSwap(
            UUID userId, int month, int year, String messageLow) {

        List<Budget> budgets = budgetRepository.findByUserIdMonthYear(userId, month, year);
        if (budgets.isEmpty()) return Collections.emptyList();

        List<Budget> requested = budgets.stream()
                .filter(b -> messageLow.contains(b.getCategory().name().toLowerCase())
                          || messageLow.contains(translateCategoryToVietnamese(b.getCategory().name())))
                .limit(2)
                .collect(Collectors.toList());
        if (requested.isEmpty()) return Collections.emptyList();

        Set<String> requestedCategories = requested.stream()
                .map(b -> b.getCategory().name()).collect(Collectors.toSet());
        List<Budget> offsetCandidates = budgets.stream()
                .filter(b -> !requestedCategories.contains(b.getCategory().name()))
                .sorted(Comparator.comparing(b -> b.getSpent() != null ? b.getSpent() : BigDecimal.ZERO))
                .limit(2)
                .collect(Collectors.toList());
        if (offsetCandidates.isEmpty()) return Collections.emptyList();

        BigDecimal amount = parseIncreaseAmount(messageLow);
        if (amount.compareTo(BigDecimal.ZERO) == 0) amount = DEFAULT_INCREASE_AMOUNT;

        boolean english = MessageLanguageDetector.isEnglish(messageLow);
        BigDecimal perRequested = amount.divide(BigDecimal.valueOf(requested.size()), 0, RoundingMode.HALF_UP);
        BigDecimal perOffset = amount.divide(BigDecimal.valueOf(offsetCandidates.size()), 0, RoundingMode.HALF_UP);

        List<ChatResponse.BudgetUpdateSuggestion> result = new ArrayList<>();
        for (Budget b : requested) {
            BigDecimal newLimit = b.getAmountLimit().add(perRequested);
            result.add(ChatResponse.BudgetUpdateSuggestion.builder()
                    .budgetId(b.getId().toString())
                    .category(b.getCategory().name())
                    .currentAmount(b.getAmountLimit().longValue())
                    .suggestedAmount(newLimit.longValue())
                    .reason(english
                            ? String.format("Increase %s by %,.0f VND -> new limit %,.0f VND.",
                                    b.getCategory().name(), perRequested, newLimit)
                            : String.format("Tăng %s thêm %,.0f VND → hạn mức mới %,.0f VND.",
                                    b.getCategory().name(), perRequested, newLimit))
                    .build());
        }
        for (Budget b : offsetCandidates) {
            BigDecimal newLimit = b.getAmountLimit().subtract(perOffset).max(BigDecimal.ZERO);
            result.add(ChatResponse.BudgetUpdateSuggestion.builder()
                    .budgetId(b.getId().toString())
                    .category(b.getCategory().name())
                    .currentAmount(b.getAmountLimit().longValue())
                    .suggestedAmount(newLimit.longValue())
                    .reason(english
                            ? String.format(
                                    "%s has the lowest spending this month — reduce it by %,.0f VND -> " +
                                    "%,.0f VND to keep your total budget unchanged.",
                                    b.getCategory().name(), perOffset, newLimit)
                            : String.format(
                                    "%s có mức chi thấp nhất tháng này — giảm %,.0f VND → %,.0f VND để giữ " +
                                    "tổng ngân sách không đổi.",
                                    b.getCategory().name(), perOffset, newLimit))
                    .build());
        }
        return result;
    }

    private BigDecimal parseIncreaseAmount(String text) {
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
