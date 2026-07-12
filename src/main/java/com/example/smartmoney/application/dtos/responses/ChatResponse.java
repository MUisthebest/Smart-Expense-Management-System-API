// Path: src/main/java/com/example/smartmoney/application/dtos/responses/ChatResponse.java

package com.example.smartmoney.application.dtos.responses;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

/**
 * Response for {@code POST /api/v1/assistant/chat}.
 *
 * <p>The frontend uses {@code intent} to decide which UI to render:
 * <ul>
 *   <li>{@code GENERAL_ADVICE}  — plain chat bubble.</li>
 *   <li>{@code BUDGET_UPDATE}   — confirm card; on accept, call {@code PUT /api/v1/budgets/{id}}.</li>
 *   <li>{@code SIMULATION}      — what-if result panel.</li>
 *   <li>{@code PROJECT_UPDATE}  — confirm card; on accept, call
 *       {@code PUT /api/v1/projects/{projectId}/tracking}.</li>
 *   <li>{@code PROJECT_HYPOTHESIS} — confirm card for a structural change (create/shorten/
 *       extend/delete); on accept, POST the returned {@code pendingActionId} to
 *       {@code /api/v1/ai/chat/confirm} — the backend executes it directly.</li>
 *   <li>{@code OUT_OF_SCOPE}    — polite redirect message.</li>
 * </ul>
 */
@Value
@Builder(toBuilder = true)
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
     * Set alongside {@code budgetSuggestions} only when those suggestions target a month other
     * than the one the turn was asked about — currently: a budget-cut request always computes a
     * full per-category plan for the FOLLOWING month only (never the current month, never more
     * than one month ahead), via {@code ComputeBudgetAllocationUseCase}. Null when the
     * suggestions apply to the queried month as usual (e.g. the increase-swap path).
     */
    Integer suggestedMonth;
    Integer suggestedYear;

    /**
     * Populated only when {@code intent == SIMULATION}.
     */
    SimulationResult simulationResult;

    /**
     * Populated when {@code intent == INCOME_WINDFALL}, {@code LARGE_EXPENSE}, or
     * {@code PROJECT_UPDATE}. Each entry maps to
     * {@code PUT /api/v1/projects/{projectId}/tracking}.
     */
    List<SavingsPlanSuggestion> savingsSuggestions;

    /**
     * Populated only when {@code intent == PROJECT_HYPOTHESIS} — a structural project change
     * (create / shorten / extend / delete), as opposed to {@code PROJECT_UPDATE}'s
     * contribution/debt-amount adjustments.
     */
    List<ProjectChangeSuggestion> projectChangeSuggestions;

    /**
     * Set only when this turn's suggestion is one the backend can execute itself: a
     * {@code PROJECT_HYPOTHESIS} change or a budget-increase swap. The client calls
     * {@code POST /api/v1/ai/chat/confirm} with this id to actually apply (or discard) it —
     * the chatbot never mutates data on its own without that explicit second call. Null for
     * every other suggestion type, which stay advisory-only (frontend calls the real endpoint
     * itself after its own confirm UI, unchanged from before).
     */
    String pendingActionId;

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

    /**
     * True when {@code reply} (and this turn's message) is in English, false for Vietnamese —
     * from {@code MessageLanguageDetector}, same "default to Vietnamese when inconclusive"
     * convention as everywhere else this is detected. Lets the frontend pick the confirm/deny
     * button wording ("Có"/"Không" vs "Yes"/"No") to match the actual conversation language
     * instead of the app's fixed UI locale, which may differ from what the user is typing in.
     */
    boolean english;

    // ── Nested types ──────────────────────────────────────────────────────────

    public enum ChatIntent {
        GENERAL_ADVICE,
        BUDGET_UPDATE,
        SIMULATION,
        INCOME_WINDFALL,
        LARGE_EXPENSE,
        PROJECT_UPDATE,
        PROJECT_HYPOTHESIS,
        OUT_OF_SCOPE
    }

    public enum ProjectChangeOperation { CREATE, SHORTEN, EXTEND, DELETE }

    /**
     * One structural project-change preview (create / shorten / extend / delete a project),
     * paired with its before/after impact on {@code available_budget = net_income −
     * Σ monthly_saving of all active projects}. Distinct from {@link SavingsPlanSuggestion},
     * which covers contribution/debt amount adjustments to an existing project's tracking.
     */
    @Value
    @Builder
    public static class ProjectChangeSuggestion {
        /** Null for CREATE — the project doesn't exist yet. */
        String projectId;
        String projectName;
        ProjectChangeOperation operation;
        /** Target amount in VND. Set for CREATE; echoed for SHORTEN/EXTEND when unchanged. */
        Long targetAmount;
        /** Null for CREATE. */
        LocalDate currentDeadline;
        /** Null for DELETE. */
        LocalDate newDeadline;
        /** Null for CREATE. */
        Long currentMonthlySaving;
        /** Null for DELETE. */
        Long newMonthlySaving;
        Long availableBudgetBefore;
        Long availableBudgetAfter;
        String reason;
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
     *
     * <p>PROJECT_UPDATE: the user directly asked to adjust a project's tracked income
     * (extra contribution — same {@code moneySaved} fields as INCOME_WINDFALL) or expense
     * ({@code currentMoneyOwed}/{@code suggestedMoneyOwed} set — debt increase/payoff).
     * Frontend calls {@code PUT /api/v1/projects/{projectId}/tracking}
     * with body {@code {moneyOwed: suggestedMoneyOwed}}.
     */
    @Value
    @Builder
    public static class SavingsPlanSuggestion {
        String projectId;
        String projectName;
        Long currentMoneySaved;
        Long suggestedAddAmount;     // > 0 for windfall/contribution, < 0 for withdrawing idle funds from an abandoned project; null when unrelated to moneySaved
        Long newMoneySaved;          // currentMoneySaved + suggestedAddAmount; null when unrelated to moneySaved
        Integer suggestedMonthLeft;  // extended months left (null unless deferring)
        Long currentMoneyOwed;       // project's tracked debt before the change; null unless PROJECT_UPDATE expense
        Long suggestedMoneyOwed;     // project's tracked debt after the change; null unless PROJECT_UPDATE expense
        String reason;
    }
}