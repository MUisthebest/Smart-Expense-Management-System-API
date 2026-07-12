package com.example.smartmoney.application.dtos.responses.budgets;

import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of {@code POST /api/v1/budgets/allocation/compute}: a deterministic per-category budget
 * plan plus the envelope figures it was derived from. Limits are whole-month; {@code spent} is not
 * part of the plan (it is backfilled when the budget is actually created). See
 * docs/deterministic-budget-allocation-plan.md.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BudgetAllocationPlanResponse {

    private Integer month;
    private Integer year;

    /** Declared monthly income. */
    private BigDecimal income;
    /** Income minus active project monthly commitment. */
    private BigDecimal safeSpending;
    /** Portion reserved for savings after crediting project commitments. */
    private BigDecimal targetSavings;
    /** Amount actually distributed across categories (safeSpending − targetSavings, floored at 0). */
    private BigDecimal envelope;
    /** Envelope left unallocated because needs were already met — destined for savings. */
    private BigDecimal surplusToSavings;

    /** True when the plan came from a trait-tilted template (no usable history). */
    private boolean coldStart;
    /** True when commitments + target savings leave nothing to allocate (all limits zero). */
    private boolean overCommitted;

    private List<CategoryAllocation> allocations;

    @Data
    @Builder
    public static class CategoryAllocation {
        private Category category;
        /** Proposed whole-month limit for the category. */
        private BigDecimal amount;
        /** The fixed (recurring) portion contained within {@code amount}. */
        private BigDecimal recurring;
        /** How the amount was derived: HISTORY, TEMPLATE, or FLOORED (raised to this month's spend). */
        private String basis;
    }
}
