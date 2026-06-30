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

    // ── Nested types ──────────────────────────────────────────────────────────

    public enum ChatIntent {
        GENERAL_ADVICE,
        BUDGET_UPDATE,
        SIMULATION,
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
}