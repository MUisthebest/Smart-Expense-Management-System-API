// Path: src/main/java/com/example/smartmoney/application/dtos/responses/ChatResponse.java

package com.example.smartmoney.application.dtos.responses;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Response for {@code POST /api/v1/assistant/chat}.
 *
 * <p>The frontend uses {@code intent} to decide which UI to render:
 * <ul>
 *   <li>{@code GENERAL_ADVICE} — plain chat bubble.</li>
 *   <li>{@code BUDGET_UPDATE}  — confirm card; on accept, call {@code PUT /api/v1/budgets/{id}}.</li>
 *   <li>{@code SIMULATION}     — what-if result panel.</li>
 *   <li>{@code OUT_OF_SCOPE}   — polite redirect message.</li>
 * </ul>
 */
@Value
@Builder
public class ChatResponse {

    /** Natural-language reply in Vietnamese, personalised to pace / focus / intervention. */
    String reply;

    /** Detected intent — tells the frontend which UI component to render. */
    ChatIntent intent;

    /**
     * Populated only when {@code intent == BUDGET_UPDATE}.
     * The frontend shows a confirm dialog; on accept it calls {@code PUT /api/v1/budgets/{id}}.
     * The chatbot itself never mutates budgets.
     */
    List<BudgetUpdateSuggestion> budgetSuggestions;

    /**
     * Populated only when {@code intent == SIMULATION}.
     */
    SimulationResult simulationResult;

    /**
     * Populated when {@code intent == INCOME_WINDFALL} or {@code LARGE_EXPENSE}.
     * Each entry maps to {@code PUT /api/v1/projects/{projectId}/tracking}.
     */
    List<SavingsPlanSuggestion> savingsSuggestions;

    /**
     * True when {@code budgetSuggestions} or {@code savingsSuggestions} is non-empty —
     * tells the frontend to render confirm/deny buttons for the proposed update instead
     * of inferring it from {@code intent} alone (intent stays the same even when the
     * server found no viable suggestion to propose).
     */
    boolean actionRequired;

    /**
     * 3 short follow-up questions the user could ask next, personalised to this turn's
     * reply and financial context. Always populated for successful chat turns (falls back
     * to generic finance questions if the LLM omits them); omitted for guardrail rejections.
     */
    List<String> relatedQuestions;

    // ── Nested types ──────────────────────────────────────────────────────────

    public enum ChatIntent {
        GENERAL_ADVICE,
        BUDGET_UPDATE,
        SIMULATION,
        INCOME_WINDFALL,
        LARGE_EXPENSE,
        OUT_OF_SCOPE
    }

    @Value
    @Builder
    public static class BudgetUpdateSuggestion {
        /** UUID of the existing budget record to update. */
        String budgetId;
        /** Category name, e.g. "FOOD". */
        String category;
        /** Current amountLimit in VND. */
        Long currentAmount;
        /** Recommended amountLimit in VND. */
        Long suggestedAmount;
        /** One-line explanation. */
        String reason;
    }

    @Value
    @Builder
    public static class SimulationResult {
        String scenario;
        Long newSafeSpending;
        String projectImpact;
        boolean violates20Rule;
    }

    /**
     * One savings-plan adjustment suggestion.
     *
     * <p>INCOME_WINDFALL: {@code suggestedAddAmount} > 0 — add a lump sum to the project.
     * Frontend calls {@code PUT /api/v1/projects/{projectId}/tracking}
     * with body {@code {moneySaved: newMoneySaved}}.
     *
     * <p>LARGE_EXPENSE: {@code suggestedMonthLeft} set — extend deadline to lower monthly pressure.
     * Frontend calls {@code PUT /api/v1/projects/{projectId}/tracking}
     * with body {@code {monthLeft: suggestedMonthLeft}}.
     */
    @Value
    @Builder
    public static class SavingsPlanSuggestion {
        String projectId;
        String projectName;
        Long currentMoneySaved;
        Long suggestedAddAmount;     // > 0 for windfall; null for large-expense deferral
        Long newMoneySaved;          // currentMoneySaved + suggestedAddAmount (null for deferral)
        Integer suggestedMonthLeft;  // extended months left (null for windfall)
        String reason;
    }
}