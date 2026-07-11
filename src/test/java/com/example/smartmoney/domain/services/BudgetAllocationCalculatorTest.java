package com.example.smartmoney.domain.services;

import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.user.enums.Focus_Mode;
import com.example.smartmoney.domain.models.user.enums.Intervention_Level;
import com.example.smartmoney.domain.services.BudgetAllocationCalculator.AllocationBasis;
import com.example.smartmoney.domain.services.BudgetAllocationCalculator.BudgetAllocationInputs;
import com.example.smartmoney.domain.services.BudgetAllocationCalculator.BudgetAllocationPlan;
import com.example.smartmoney.domain.services.BudgetAllocationCalculator.CategoryAllocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BudgetAllocationCalculator — deterministic allocation core")
class BudgetAllocationCalculatorTest {

    private final BudgetAllocationCalculator calculator = new BudgetAllocationCalculator();

    // ---- helpers -------------------------------------------------------------------------

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static Map<Category, BigDecimal> map(Object... kv) {
        Map<Category, BigDecimal> m = new EnumMap<>(Category.class);
        for (int i = 0; i < kv.length; i += 2) {
            m.put((Category) kv[i], bd((String) kv[i + 1]));
        }
        return m;
    }

    private static BudgetAllocationInputs inputs(String envelope, Map<Category, BigDecimal> median,
                                                 Map<Category, BigDecimal> recurring,
                                                 Map<Category, BigDecimal> spent, int monthsOfHistory) {
        return new BudgetAllocationInputs(bd(envelope), median, recurring, spent,
                Focus_Mode.TRACK_ONLY, Intervention_Level.GENTLE, monthsOfHistory);
    }

    private static BigDecimal amountOf(BudgetAllocationPlan plan, Category c) {
        return plan.allocations().stream().filter(a -> a.category() == c).findFirst()
                .map(CategoryAllocation::amount).orElseThrow();
    }

    private static AllocationBasis basisOf(BudgetAllocationPlan plan, Category c) {
        return plan.allocations().stream().filter(a -> a.category() == c).findFirst()
                .map(CategoryAllocation::basis).orElseThrow();
    }

    private static BigDecimal sumAmounts(BudgetAllocationPlan plan) {
        return plan.allocations().stream().map(CategoryAllocation::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, bd(expected).compareTo(actual), () -> "expected " + expected + " but got " + actual);
    }

    // ---- Stage 0: over-committed ---------------------------------------------------------

    @Test
    @DisplayName("envelope <= 0 → over-committed plan, all limits zero, no surplus")
    void overCommitted() {
        BudgetAllocationPlan plan = calculator.allocate(
                inputs("0", map(Category.FOOD, "5000000"), map(), map(), 3));

        assertTrue(plan.overCommitted());
        assertFalse(plan.coldStart());
        assertMoney("0", amountOf(plan, Category.FOOD));
        assertMoney("0", plan.surplusToSavings());
    }

    // ---- established: needs-based with surplus → savings ---------------------------------

    @Test
    @DisplayName("under the envelope → fund needs (+buffer), bank the rest as surplus")
    void underEnvelopeBanksSurplus() {
        BudgetAllocationPlan plan = calculator.allocate(
                inputs("16000000", map(Category.FOOD, "5000000", Category.TRANSPORTATION, "1500000"),
                        map(), map(), 3));

        assertFalse(plan.coldStart());
        assertFalse(plan.overCommitted());
        // GENTLE buffer 0.10: 5M→5.5M, 1.5M→1.65M
        assertMoney("5500000", amountOf(plan, Category.FOOD));
        assertMoney("1650000", amountOf(plan, Category.TRANSPORTATION));
        assertEquals(AllocationBasis.HISTORY, basisOf(plan, Category.FOOD));
        assertMoney("8850000", plan.surplusToSavings()); // 16M - 7.15M
        // Invariant: allocations + surplus reconstruct the envelope exactly.
        assertMoney("16000000", sumAmounts(plan).add(plan.surplusToSavings()));
    }

    @Test
    @DisplayName("over the envelope → scale every category down proportionally to fit, no surplus")
    void overEnvelopeScalesDown() {
        BudgetAllocationPlan plan = calculator.allocate(
                inputs("4000000", map(Category.FOOD, "5000000", Category.TRANSPORTATION, "1500000"),
                        map(), map(), 3));

        assertMoney("0", plan.surplusToSavings());
        // Rounding is reconciled so the amounts sum back to the envelope exactly.
        assertMoney("4000000", sumAmounts(plan));
        // Proportions preserved: FOOD stays the larger share.
        assertTrue(amountOf(plan, Category.FOOD).compareTo(amountOf(plan, Category.TRANSPORTATION)) > 0);
    }

    @Test
    @DisplayName("recurring cost floors the category; only the variable part gets the buffer")
    void recurringSplitProtectsFixedCost() {
        BudgetAllocationPlan plan = calculator.allocate(
                inputs("16000000",
                        map(Category.FOOD, "5000000", Category.TRANSPORTATION, "2000000"),
                        map(Category.FOOD, "5000000"), // FOOD is entirely recurring → variable 0
                        map(), 3));

        assertMoney("5000000", amountOf(plan, Category.FOOD));       // recurring only, no buffer added
        assertMoney("5000000", plan.allocations().stream()
                .filter(a -> a.category() == Category.FOOD).findFirst().orElseThrow().recurring());
        assertMoney("2200000", amountOf(plan, Category.TRANSPORTATION)); // 2M + 10% buffer
    }

    // ---- Stage 3: mid-month floor --------------------------------------------------------

    @Test
    @DisplayName("mid-month: a category already over its computed limit is floored to this month's spend")
    void midMonthFloorRaisesToSpent() {
        BudgetAllocationPlan plan = calculator.allocate(
                inputs("16000000", map(Category.FOOD, "5000000"), map(),
                        map(Category.FOOD, "7000000"), 3)); // already spent 7M > computed 5.5M

        assertMoney("7000000", amountOf(plan, Category.FOOD));
        assertEquals(AllocationBasis.FLOORED, basisOf(plan, Category.FOOD));
        assertMoney("9000000", plan.surplusToSavings()); // 16M - 7M
    }

    // ---- cold start ----------------------------------------------------------------------

    @Test
    @DisplayName("no history → trait template fills the envelope exactly, no surplus")
    void coldStartFillsEnvelope() {
        BudgetAllocationPlan plan = calculator.allocate(
                inputs("10000000", map(), map(), map(), 0));

        assertTrue(plan.coldStart());
        assertEquals(AllocationBasis.TEMPLATE, basisOf(plan, Category.FOOD));
        assertMoney("3000000", amountOf(plan, Category.FOOD)); // 30% template share
        assertMoney("10000000", sumAmounts(plan));
        assertMoney("0", plan.surplusToSavings());
    }

    @Test
    @DisplayName("history months present but all medians zero → still treated as cold start")
    void zeroMedianFallsBackToColdStart() {
        BudgetAllocationPlan plan = calculator.allocate(
                inputs("10000000", map(), map(), map(), 5));

        assertTrue(plan.coldStart());
        assertMoney("10000000", sumAmounts(plan));
    }

    @Test
    @DisplayName("cold start + save-oriented focus trims 'wants' below equally-weighted needs")
    void coldStartSaveTiltTrimsWants() {
        BudgetAllocationInputs in = new BudgetAllocationInputs(bd("10000000"), map(), map(), map(),
                Focus_Mode.SAVE_MORE, Intervention_Level.GENTLE, 0);

        BudgetAllocationPlan plan = calculator.allocate(in);

        // ENTERTAINMENT (a want, tilted ×0.7 → 0.056) drops below HEALTH (a need, 0.08).
        assertTrue(amountOf(plan, Category.ENTERTAINMENT).compareTo(amountOf(plan, Category.HEALTH)) < 0);
        assertMoney("10000000", sumAmounts(plan));
    }

    // ---- intervention buffer -------------------------------------------------------------

    @Test
    @DisplayName("HARD intervention uses a tighter buffer than NOTIFY")
    void interventionLevelControlsBuffer() {
        Map<Category, BigDecimal> median = map(Category.FOOD, "5000000");
        BudgetAllocationPlan hard = calculator.allocate(new BudgetAllocationInputs(
                bd("16000000"), median, map(), map(), Focus_Mode.TRACK_ONLY, Intervention_Level.HARD, 3));
        BudgetAllocationPlan notify = calculator.allocate(new BudgetAllocationInputs(
                bd("16000000"), median, map(), map(), Focus_Mode.TRACK_ONLY, Intervention_Level.NOTIFY, 3));

        assertMoney("5250000", amountOf(hard, Category.FOOD));   // 5M + 5% buffer
        assertMoney("6000000", amountOf(notify, Category.FOOD)); // 5M + 20% buffer
    }
}
