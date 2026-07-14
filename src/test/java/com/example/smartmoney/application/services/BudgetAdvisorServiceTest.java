package com.example.smartmoney.application.services;

import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("BudgetAdvisorService")
class BudgetAdvisorServiceTest {

    private final BudgetRepository budgetRepository = mock(BudgetRepository.class);
    private final BudgetAdvisorService service = new BudgetAdvisorService(budgetRepository);

    private Budget budget(BigDecimal limit, BigDecimal spent) {
        return Budget.builder()
                .id(UUID.randomUUID())
                .category(Category.FOOD)
                .amountLimit(limit)
                .spent(spent)
                .build();
    }

    @Test
    @DisplayName("checkNearLimit fires once a budget crosses the 80% threshold")
    void nearLimitFires() {
        when(budgetRepository.findByUserIdMonthYear(any(), anyInt(), anyInt()))
                .thenReturn(List.of(budget(BigDecimal.valueOf(2_000_000), BigDecimal.valueOf(1_700_000))));

        var result = service.checkNearLimit(UUID.randomUUID(), 7, 2026, 5, false);

        assertThat(result).isPresent();
        assertThat(result.get().getCategory()).isEqualTo("FOOD");
        assertThat(result.get().getCurrentAmount()).isEqualTo(2_000_000L);
    }

    @Test
    @DisplayName("checkNearLimit is empty when every budget is comfortably under the threshold")
    void nearLimitEmptyWhenUnderThreshold() {
        when(budgetRepository.findByUserIdMonthYear(any(), anyInt(), anyInt()))
                .thenReturn(List.of(budget(BigDecimal.valueOf(2_000_000), BigDecimal.valueOf(500_000))));

        assertThat(service.checkNearLimit(UUID.randomUUID(), 7, 2026, 5, false)).isEmpty();
    }

    @Test
    @DisplayName("checkNearLimit is empty when the user has no budgets set up")
    void nearLimitEmptyWhenNoBudgets() {
        when(budgetRepository.findByUserIdMonthYear(any(), anyInt(), anyInt())).thenReturn(List.of());

        assertThat(service.checkNearLimit(UUID.randomUUID(), 7, 2026, 5, false)).isEmpty();
    }

    // ── suggestIncreaseSwap ───────────────────────────────────────────────────

    private Budget budget(Category category, BigDecimal limit, BigDecimal spent) {
        return Budget.builder().id(UUID.randomUUID()).category(category)
                .amountLimit(limit).spent(spent).build();
    }

    @Test
    @DisplayName("suggestIncreaseSwap raises the requested category and offsets from the lowest-spend one(s)")
    void increaseSwapOffsetsFromLowestSpend() {
        when(budgetRepository.findByUserIdMonthYear(any(), anyInt(), anyInt())).thenReturn(List.of(
                budget(Category.FOOD, BigDecimal.valueOf(3_000_000), BigDecimal.valueOf(1_000_000)),
                budget(Category.ENTERTAINMENT, BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(100_000)),
                budget(Category.TRANSPORTATION, BigDecimal.valueOf(2_000_000), BigDecimal.valueOf(1_800_000))));

        var result = service.suggestIncreaseSwap(UUID.randomUUID(), 7, 2026, "tôi muốn tăng ngân sách ăn thêm 1 triệu");

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getCategory()).isEqualTo("FOOD");
        assertThat(result.get(0).getSuggestedAmount()).isEqualTo(4_000_000L);

        var offsets = result.subList(1, 3);
        assertThat(offsets).extracting(s -> s.getCategory())
                .containsExactlyInAnyOrder("ENTERTAINMENT", "TRANSPORTATION");
        var entertainment = offsets.stream().filter(s -> s.getCategory().equals("ENTERTAINMENT")).findFirst().get();
        assertThat(entertainment.getSuggestedAmount()).isEqualTo(500_000L);
    }

    @Test
    @DisplayName("suggestIncreaseSwap is empty when no category keyword matches")
    void increaseSwapEmptyWithoutCategoryMatch() {
        when(budgetRepository.findByUserIdMonthYear(any(), anyInt(), anyInt())).thenReturn(List.of(
                budget(Category.FOOD, BigDecimal.valueOf(3_000_000), BigDecimal.valueOf(1_000_000))));

        assertThat(service.suggestIncreaseSwap(UUID.randomUUID(), 7, 2026, "tôi muốn tăng ngân sách thêm 1 triệu"))
                .isEmpty();
    }

    @Test
    @DisplayName("suggestIncreaseSwap is empty when there's no other category to offset from")
    void increaseSwapEmptyWithoutOffsetCandidate() {
        when(budgetRepository.findByUserIdMonthYear(any(), anyInt(), anyInt())).thenReturn(List.of(
                budget(Category.FOOD, BigDecimal.valueOf(3_000_000), BigDecimal.valueOf(1_000_000))));

        assertThat(service.suggestIncreaseSwap(UUID.randomUUID(), 7, 2026, "tôi muốn tăng ngân sách ăn thêm 1 triệu"))
                .isEmpty();
    }
}
