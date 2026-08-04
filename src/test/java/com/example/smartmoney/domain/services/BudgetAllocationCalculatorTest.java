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
        return new BudgetAllocationInputs(bd(envelope), BigDecimal.ZERO, median, recurring, spent,
                Focus_Mode.TRACK_ONLY, Intervention_Level.GENTLE, false, monthsOfHistory);
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
    @DisplayName("over the envelope → scale down proportionally, snap to the step, shave → surplus")
    void overEnvelopeScalesDown() {
        BudgetAllocationPlan plan = calculator.allocate(
                inputs("4000000", map(Category.FOOD, "5000000", Category.TRANSPORTATION, "1500000"),
                        map(), map(), 3));

        // Raw scaled shares (3,076,923.08 / 923,076.92) snap down to clean 10k multiples.
        assertMoney("3070000", amountOf(plan, Category.FOOD));
        assertMoney("920000", amountOf(plan, Category.TRANSPORTATION));
        // The shaved remainder rolls into savings; allocations + surplus still cover the envelope.
        assertMoney("10000", plan.surplusToSavings());
        assertMoney("4000000", sumAmounts(plan).add(plan.surplusToSavings()));
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
        // The helper is TRACK_ONLY with zero savings and no projects, so the floored category's
        // top-up has no funding source: FOOD stays exactly at its spent floor.
        BudgetAllocationPlan plan = calculator.allocate(
                inputs("16000000", map(Category.FOOD, "5000000"), map(),
                        map(Category.FOOD, "7000000"), 3)); // already spent 7M > computed 5.5M

        assertMoney("7000000", amountOf(plan, Category.FOOD));
        assertEquals(AllocationBasis.FLOORED, basisOf(plan, Category.FOOD));
        assertMoney("9000000", plan.surplusToSavings()); // 16M - 7M
        assertMoney("0", plan.savingsShortfall());
    }

    @Test
    @DisplayName("floored category gets a forward allowance from the surplus, not a zero-headroom budget")
    void flooredCategoryToppedUpFromSurplus() {
        // Non-TRACK_ONLY user: FOOD floored to its 7M spend, then granted a 10% (GENTLE)
        // allowance of max(spent, median) = 700k, paid out of the 9M surplus.
        BudgetAllocationPlan plan = calculator.allocate(new BudgetAllocationInputs(
                bd("16000000"), BigDecimal.ZERO,
                map(Category.FOOD, "5000000"), map(), map(Category.FOOD, "7000000"),
                Focus_Mode.REDUCE_SPENDING, Intervention_Level.GENTLE, false, 3));

        assertMoney("7700000", amountOf(plan, Category.FOOD));
        assertEquals(AllocationBasis.FLOORED, basisOf(plan, Category.FOOD));
        assertMoney("8300000", plan.surplusToSavings()); // 16M - 7.7M
        assertMoney("0", plan.savingsShortfall());
        // Invariant: allocations + surplus still reconstruct the envelope exactly.
        assertMoney("16000000", sumAmounts(plan).add(plan.surplusToSavings()));
    }

    @Test
    @DisplayName("TRACK_ONLY without projects: the top-up draws on savings, surplus stays untouched")
    void trackOnlyWithoutProjectsKeepsSurplusIntact() {
        BudgetAllocationPlan plan = calculator.allocate(new BudgetAllocationInputs(
                bd("16000000"), bd("3000000"),
                map(Category.FOOD, "5000000"), map(), map(Category.FOOD, "7000000"),
                Focus_Mode.TRACK_ONLY, Intervention_Level.GENTLE, false, 3));

        assertMoney("7700000", amountOf(plan, Category.FOOD));
        // Surplus is exactly what it was before the top-up (16M - 7M) — nothing deducted.
        assertMoney("9000000", plan.surplusToSavings());
        // The 700k allowance is billed to the savings ambition instead.
        assertMoney("700000", plan.savingsShortfall());
        // Invariant: allocations + surplus == envelope + savings draw.
        assertMoney("16700000", sumAmounts(plan).add(plan.surplusToSavings()));
    }

    @Test
    @DisplayName("TRACK_ONLY with an active project: the top-up is funded from surplus as usual")
    void trackOnlyWithProjectsFundsTopUpFromSurplus() {
        BudgetAllocationPlan plan = calculator.allocate(new BudgetAllocationInputs(
                bd("16000000"), bd("3000000"),
                map(Category.FOOD, "5000000"), map(), map(Category.FOOD, "7000000"),
                Focus_Mode.TRACK_ONLY, Intervention_Level.GENTLE, true, 3));

        assertMoney("7700000", amountOf(plan, Category.FOOD));
        assertMoney("8300000", plan.surplusToSavings()); // 16M - 7.7M
        assertMoney("0", plan.savingsShortfall());       // savings ambition stays whole
    }

    @Test
    @DisplayName("floor overage is clawed back from the other categories' unspent headroom")
    void floorOverageShrinksOtherCategories() {
        // Targets 5.5M + 5.5M = 11M > 10M envelope → scaled to 5M each. FOOD already spent 7M
        // → floored to 7M; the 2M overage comes out of TRANSPORTATION's untouched headroom.
        BudgetAllocationPlan plan = calculator.allocate(
                inputs("10000000",
                        map(Category.FOOD, "5000000", Category.TRANSPORTATION, "5000000"),
                        map(),
                        map(Category.FOOD, "7000000"), 3));

        assertMoney("7000000", amountOf(plan, Category.FOOD));
        assertEquals(AllocationBasis.FLOORED, basisOf(plan, Category.FOOD));
        assertMoney("3000000", amountOf(plan, Category.TRANSPORTATION));
        assertEquals(AllocationBasis.HISTORY, basisOf(plan, Category.TRANSPORTATION));
        // The plan fits the envelope again: nothing overflows, nothing left over.
        assertMoney("10000000", sumAmounts(plan));
        assertMoney("0", plan.surplusToSavings());
        assertMoney("0", plan.savingsShortfall());
    }

    @Test
    @DisplayName("headroom exhausted → the residual overage is reported as savingsShortfall")
    void floorOverageBeyondHeadroomReportsSavingsShortfall() {
        // Scaled to 5M each; FOOD floored to 7M (overage 2M) but TRANSPORTATION only has 1M of
        // headroom above its own 4M spent floor → it shrinks to 4M and the remaining 1M is
        // reported for the caller to fund from targetSavings.
        BudgetAllocationPlan plan = calculator.allocate(
                inputs("10000000",
                        map(Category.FOOD, "5000000", Category.TRANSPORTATION, "5000000"),
                        map(),
                        map(Category.FOOD, "7000000", Category.TRANSPORTATION, "4000000"), 3));

        assertMoney("7000000", amountOf(plan, Category.FOOD));
        assertMoney("4000000", amountOf(plan, Category.TRANSPORTATION));
        assertMoney("11000000", sumAmounts(plan)); // envelope 10M + 1M shortfall
        assertMoney("0", plan.surplusToSavings());
        assertMoney("1000000", plan.savingsShortfall());
    }

    // ---- focusMode controls the overage order --------------------------------------------

    @Test
    @DisplayName("TRACK_ONLY: savings absorb the floor overage first, other categories untouched")
    void trackOnlyDrawsSavingsBeforeShrinkingCategories() {
        // Same shape as floorOverageShrinksOtherCategories (overage 2M), but 3M of savings is
        // available — a TRACK_ONLY user keeps TRANSPORTATION at 5M and gives up savings instead.
        BudgetAllocationPlan plan = calculator.allocate(new BudgetAllocationInputs(
                bd("10000000"), bd("3000000"),
                map(Category.FOOD, "5000000", Category.TRANSPORTATION, "5000000"),
                map(), map(Category.FOOD, "7000000"),
                Focus_Mode.TRACK_ONLY, Intervention_Level.GENTLE, false, 3));

        // FOOD's 700k forward allowance also comes out of the remaining 1M of savings
        // (no projects → surplus is never tapped, and there is none here anyway).
        assertMoney("7700000", amountOf(plan, Category.FOOD));
        assertMoney("5000000", amountOf(plan, Category.TRANSPORTATION));
        assertMoney("2700000", plan.savingsShortfall()); // funded from targetSavings by the caller
    }

    @Test
    @DisplayName("TRACK_ONLY: savings only partially cover → categories shrink just the remainder")
    void trackOnlyShrinksOnlyWhatSavingsCannotCover() {
        // Overage 2M, savings 1M → TRANSPORTATION gives up only the uncovered 1M.
        BudgetAllocationPlan plan = calculator.allocate(new BudgetAllocationInputs(
                bd("10000000"), bd("1000000"),
                map(Category.FOOD, "5000000", Category.TRANSPORTATION, "5000000"),
                map(), map(Category.FOOD, "7000000"),
                Focus_Mode.TRACK_ONLY, Intervention_Level.GENTLE, false, 3));

        assertMoney("7000000", amountOf(plan, Category.FOOD));
        assertMoney("4000000", amountOf(plan, Category.TRANSPORTATION));
        assertMoney("1000000", plan.savingsShortfall());
    }

    @Test
    @DisplayName("save-oriented focus: categories shrink first even when savings are available")
    void saveOrientedShrinksCategoriesBeforeSavings() {
        BudgetAllocationPlan plan = calculator.allocate(new BudgetAllocationInputs(
                bd("10000000"), bd("3000000"),
                map(Category.FOOD, "5000000", Category.TRANSPORTATION, "5000000"),
                map(), map(Category.FOOD, "7000000"),
                Focus_Mode.SAVE_MORE, Intervention_Level.GENTLE, false, 3));

        // The 2M overage AND FOOD's 700k forward allowance both come out of TRANSPORTATION's
        // headroom (no surplus to tap): 5M − 2M − 0.7M. Savings are never touched.
        assertMoney("7700000", amountOf(plan, Category.FOOD));
        assertMoney("2300000", amountOf(plan, Category.TRANSPORTATION));
        assertMoney("0", plan.savingsShortfall()); // savings ambition stays intact
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
    @DisplayName("fewer than 3 months of history → still treated as cold start")
    void historyLessThan3MonthsTreatedAsColdStart() {
        BudgetAllocationPlan plan1 = calculator.allocate(
                inputs("10000000", map(Category.FOOD, "5000000"), map(), map(), 1));
        BudgetAllocationPlan plan2 = calculator.allocate(
                inputs("10000000", map(Category.FOOD, "5000000"), map(), map(), 2));

        assertTrue(plan1.coldStart());
        assertTrue(plan2.coldStart());
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
        BudgetAllocationInputs in = new BudgetAllocationInputs(bd("10000000"), BigDecimal.ZERO,
                map(), map(), map(), Focus_Mode.SAVE_MORE, Intervention_Level.GENTLE, false, 0);

        BudgetAllocationPlan plan = calculator.allocate(in);

        // ENTERTAINMENT (a want, tilted ×0.7 → 0.056) drops below HEALTH (a need, 0.08).
        assertTrue(amountOf(plan, Category.ENTERTAINMENT).compareTo(amountOf(plan, Category.HEALTH)) < 0);
        // Tilted weights don't divide evenly; snapping shaves the odd remainders into surplus.
        assertMoney("10000000", sumAmounts(plan).add(plan.surplusToSavings()));
    }

    // ---- step snapping -------------------------------------------------------------------

    @Test
    @DisplayName("every proposed limit is a clean multiple of the 10k step")
    void allocationsSnapToStep() {
        BudgetAllocationPlan plan = calculator.allocate(
                inputs("4000000", map(Category.FOOD, "5000000", Category.TRANSPORTATION, "1500000"),
                        map(), map(Category.TRANSPORTATION, "923456"), 3));

        for (CategoryAllocation a : plan.allocations()) {
            assertMoney("0", a.amount().remainder(bd("10000")));
        }
        // TRANSPORTATION's snapped-down share would dip below its 923,456 spent floor,
        // so the spent amount is snapped up instead of down.
        assertMoney("930000", amountOf(plan, Category.TRANSPORTATION));
    }

    // ---- intervention buffer -------------------------------------------------------------

    @Test
    @DisplayName("HARD intervention uses a tighter buffer than NOTIFY")
    void interventionLevelControlsBuffer() {
        Map<Category, BigDecimal> median = map(Category.FOOD, "5000000");
        BudgetAllocationPlan hard = calculator.allocate(new BudgetAllocationInputs(
                bd("16000000"), BigDecimal.ZERO, median, map(), map(),
                Focus_Mode.TRACK_ONLY, Intervention_Level.HARD, false, 3));
        BudgetAllocationPlan notify = calculator.allocate(new BudgetAllocationInputs(
                bd("16000000"), BigDecimal.ZERO, median, map(), map(),
                Focus_Mode.TRACK_ONLY, Intervention_Level.NOTIFY, false, 3));

        assertMoney("5250000", amountOf(hard, Category.FOOD));   // 5M + 5% buffer
        assertMoney("6000000", amountOf(notify, Category.FOOD)); // 5M + 20% buffer
    }
}
