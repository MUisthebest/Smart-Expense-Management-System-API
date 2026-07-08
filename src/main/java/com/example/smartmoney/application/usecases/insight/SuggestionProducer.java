package com.example.smartmoney.application.usecases.insight;

import com.example.smartmoney.application.usecases.notification.CreateNotificationUseCase;
import com.example.smartmoney.application.usecases.settlement.SettlementProperties;
import com.example.smartmoney.application.usecases.suggestion.SuggestionPayload;
import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.insight.Insight;
import com.example.smartmoney.domain.models.insight.InsightType;
import com.example.smartmoney.domain.models.insight.Severity;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.suggestion.Suggestion;
import com.example.smartmoney.domain.models.suggestion.SuggestionTopicStat;
import com.example.smartmoney.domain.models.suggestion.SuggestionType;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Focus_Mode;
import com.example.smartmoney.domain.models.user.enums.Intervention_Level;
import com.example.smartmoney.domain.models.user.enums.Saving_Pace;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.SuggestionRepository;
import com.example.smartmoney.domain.repositories.SuggestionTopicStatRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.services.SpendingInsightAnalyzer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Proactive half of workstream C, extended for Phase 2. On a transaction create it runs the
 * analyzer and — gated by the user's {@code interventionLevel} trait — may persist a
 * {@link Suggestion} and push a deep-linked notification:
 * <ul>
 *   <li>EXPENSE → {@code RAISE_BUDGET} (BUDGET_PACE overshoot, category has a budget) or
 *       {@code CREATE_BUDGET} (OVERSPEND_ANOMALY, category has no budget) — P2-B.</li>
 *   <li>INCOME → {@code CONTRIBUTE_TO_PROJECT} (SAVINGS_CAPACITY headroom toward an active
 *       saving project, amount scaled by {@code savingPace}) — P2-B/D/E.</li>
 * </ul>
 *
 * <p>Best-effort: every failure here is swallowed so a producer hiccup never affects the user's
 * transaction. Fatigue is controlled in layers: (a) NOTIFY users get zero pushes, (b) the severity
 * gate, (c) one open ask per topic via dedup, (d) dismissal suppression (P2-A), (e) a per-topic
 * cooldown, and (f) a per-user daily push cap (P3-A). Controls (d) and (e) fully skip the ask; the
 * daily cap (f) instead <em>demotes</em> the push — the suggestion card is still created (actionable
 * in-app) but a single daily roll-up ping replaces the individual push, so overflow is never silently
 * lost. Accepted suggestions don't count toward the cap (engagement isn't fatigue). A CRITICAL
 * escalation bypasses (d)–(f) entirely, so a genuine emergency is never silenced.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SuggestionProducer {

    /** Dismissals of a topic at/above this count suppress it unless severity escalates to CRITICAL. */
    static final int SUPPRESS_AFTER_DISMISSALS = 2;
    /** Headroom over the median actual when proposing a reduced budget limit (REDUCE_BUDGET). */
    private static final BigDecimal REDUCE_HEADROOM = new BigDecimal("1.15");
    /** Floor for a rebalanced budget limit — never propose scaling a limit below one cent. */
    private static final BigDecimal MIN_LIMIT = new BigDecimal("0.01");
    /** Smallest overshoot worth a REALLOCATE_BUDGET ask — don't nag to move a trivial amount. */
    private static final BigDecimal MIN_MOVE = new BigDecimal("1.00");
    /** Trailing closed months over which a project's observed contribution rate is averaged. */
    static final int OFF_TRACK_OBSERVE_MONTHS = 3;
    /** Required-rate ÷ observed-rate at or above which an off-track project escalates to CRITICAL. */
    private static final BigDecimal OFF_TRACK_CRITICAL_RATIO = new BigDecimal("2");
    /** Default horizon (months) over which a first saving project's target is seeded from the monthly. */
    static final int CREATE_PROJECT_HORIZON_MONTHS = 6;
    /** MonthlyObligationPolicy ceiling: a project's monthly saving must stay ≤ 20% of the user's income. */
    private static final BigDecimal MONTHLY_OBLIGATION_RATE = new BigDecimal("0.20");

    private final SpendingInsightAnalyzer analyzer;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final SuggestionRepository suggestionRepository;
    private final SuggestionTopicStatRepository topicStatRepository;
    private final CreateNotificationUseCase createNotificationUseCase;
    private final ObjectMapper objectMapper;
    private final SuggestionFatigueProperties fatigue;
    private final Clock clock;
    private final InsightNarrator narrator;
    private final SettlementProperties settlement;

    /**
     * Item 1 (docs/settlement-insights-followup.md): the manual "contribute" nudges assume the user
     * funds goals by hand. When the settlement engine is live AND the user has {@code autoInvestSurplus}
     * on, settlement already routes surplus into projects, so nudging them to contribute more
     * double-asks and reads as broken — suppress. When settlement is off (the default) OR the user
     * opted out of auto-invest, the manual nudge is the primary funding path — keep it. The
     * settlement-enabled half matters because {@code autoInvestSurplus} defaults to true: without it,
     * every user's contribute nudges would vanish while settlement isn't even running.
     */
    private boolean settlementFundsProjectsFor(User user) {
        return settlement.isEnabled() && user.isAutoInvestSurplus();
    }

    /**
     * Item 6 (docs/settlement-insights-followup.md): whether to suppress the manual "save/contribute
     * more toward a goal" nudges (CONTRIBUTE_TO_PROJECT, INCREASE_CONTRIBUTION) for this user. Two
     * reasons combine:
     * <ul>
     *   <li><b>TRACK_ONLY</b> — the user opted into passive tracking; settlement never penalizes them,
     *       so the suggestion system must not keep pushing them to fund goals either (trait consistency).</li>
     *   <li><b>Settlement auto-invests</b> — see {@link #settlementFundsProjectsFor(User)} (item 1).</li>
     * </ul>
     * OVER_COMMITTED is deliberately <em>not</em> gated by this — suggesting a TRACK_ONLY user rebalance
     * down to fit their pace aligns with passive tracking rather than nagging them to save more.
     */
    private boolean suppressContributionNudges(User user) {
        return user.getFocusMode() == Focus_Mode.TRACK_ONLY || settlementFundsProjectsFor(user);
    }

    /**
     * Evaluate the affected category after an expense create and maybe raise a budget-related
     * suggestion (RAISE_BUDGET if the category has a budget, else CREATE_BUDGET). Never throws.
     */
    public void maybeProduce(UUID userId, Category affectedCategory, LocalDate asOf) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            Severity gate = pushGateFor(user == null ? null : user.getInterventionLevel());
            if (gate == null) {
                return; // NOTIFY (or unset) → pull-only user, no push
            }

            YearMonth period = YearMonth.from(asOf);
            List<Transaction> history = loadHistory(userId, asOf, period);
            List<Budget> budgets = budgetRepository.findByUserIdMonthYear(
                    userId, period.getMonthValue(), period.getYear());
            List<Insight> insights = analyzer.analyze(history, budgets, asOf);

            Budget budget = budgets.stream()
                    .filter(b -> b.getCategory() == affectedCategory)
                    .findFirst().orElse(null);

            Candidate candidate;
            if (budget != null && budget.getId() != null) {
                // Prefer a zero-sum reallocation from underspent budgets (no new money); else raise the
                // limit; else, on a healthy budget, a persistent underspend can suggest trimming it.
                candidate = reallocateBudgetCandidate(insights, affectedCategory, budget, gate, budgets, period);
                if (candidate == null) {
                    candidate = raiseBudgetCandidate(insights, affectedCategory, budget, gate, history, period, user.getIncome());
                }
                if (candidate == null) {
                    candidate = reduceBudgetCandidate(insights, affectedCategory, budget, period);
                }
            } else {
                // Anomaly (already overspending) wins; else a rising trend can suggest a first limit.
                candidate = createBudgetCandidate(insights, affectedCategory, gate, history, period, user.getIncome());
                if (candidate == null) {
                    candidate = setCategoryLimitCandidate(insights, affectedCategory, gate, history, period, user.getIncome());
                }
            }
            if (candidate == null) {
                // Wave 2: a recurring charge in this category just posted at a hiked price.
                candidate = reviewSubscriptionCandidate(insights, affectedCategory, gate, period);
            }

            persistAndPush(userId, candidate);
        } catch (Exception e) {
            log.warn("[Suggestion] expense producer failed for user {} / {}: {}",
                    userId, affectedCategory, e.getMessage());
        }
    }

    /**
     * Evaluate savings headroom after an income create and maybe suggest contributing to an
     * active saving project (P2-B/D/E). Never throws. SAVINGS_CAPACITY is an INFO insight, so the
     * gate here is presence-of-push (GENTLE/HARD), not severity.
     */
    public void maybeProduceSavingsContribution(UUID userId, LocalDate asOf) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || pushGateFor(user.getInterventionLevel()) == null) {
                return; // pull-only / unset → no push
            }

            YearMonth period = YearMonth.from(asOf);
            List<Transaction> history = loadHistory(userId, asOf, period);
            List<Budget> budgets = budgetRepository.findByUserIdMonthYear(
                    userId, period.getMonthValue(), period.getYear());
            List<Insight> insights = analyzer.analyze(history, budgets, asOf);

            Insight capacity = insights.stream()
                    .filter(i -> i.getType() == InsightType.SAVINGS_CAPACITY)
                    .findFirst().orElse(null);
            if (capacity == null) {
                return;
            }
            BigDecimal disposable = toBigDecimal(capacity.getMetrics().get("disposable"));
            if (disposable.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            Project target = pickContributionTarget(userId);
            if (target == null) {
                // Wave 4 (item 10): capacity but nowhere to put it — nudge creating a project. This
                // branch is deliberately NOT gated by suppressContributionNudges. That suppression
                // exists because settlement auto-invests surplus into EXISTING active projects, but
                // here the user has none — so auto-invest is a no-op (PerformMonthlySettlementUseCase
                // iterates an empty project list and the surplus stays as idle cash). Suppressing the
                // nudge here would silence the one signal that could put that capacity to work. Only
                // TRACK_ONLY is still honored: a passive tracker opted out of goal-setting nudges.
                if (user.getFocusMode() != Focus_Mode.TRACK_ONLY) {
                    maybeNudgeCreateProject(userId, user, capacity, disposable, period);
                }
                return;
            }

            // The manual "contribute to a goal" nudge assumes manual funding — suppressed when
            // settlement auto-invests surplus into this existing project (item 1) or the user is
            // TRACK_ONLY (item 6). Unlike the create-project branch above, the target exists here,
            // so settlement genuinely funds it and re-asking would double-nag.
            if (suppressContributionNudges(user)) {
                return;
            }

            // P2-E: scale the share of disposable by savingPace, then cap at what the project needs.
            BigDecimal amount = scaleByPace(disposable, user.getSavingPace())
                    .min(target.remaining().amount())
                    .setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            persistAndPush(userId, contributeCandidate(capacity, target, amount, period));
        } catch (Exception e) {
            log.warn("[Suggestion] savings producer failed for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Wave 2 (item 5): after a budget create or limit update, check whether the month's committed
     * budget total now exceeds the user's declared income and maybe ask to rebalance — scale every
     * limit proportionally so the total fits the income envelope. The consented numbers are
     * snapshotted per budget at ask time. Best-effort, never throws. BUDGET_OVER_INCOME is WARN,
     * so GENTLE users get the ask and HARD (CRITICAL-only) users don't — same gate as the
     * expense-path candidates.
     */
    public void maybeProduceRebalance(UUID userId, int month, int year) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            Severity gate = pushGateFor(user == null ? null : user.getInterventionLevel());
            if (gate == null) {
                return; // pull-only / unset → no push
            }

            YearMonth period = YearMonth.of(year, month);
            List<Budget> budgets = budgetRepository.findByUserIdMonthYear(userId, month, year);
            Insight insight = analyzer.detectBudgetOverIncome(budgets, user.getIncome(), period)
                    .orElse(null);
            if (insight == null || !insight.getSeverity().atLeast(gate)) {
                return; // within the envelope, or not severe enough for this user's gate
            }

            persistAndPush(userId, rebalanceCandidate(insight, budgets, user.getIncome(), period));
        } catch (Exception e) {
            log.warn("[Suggestion] rebalance producer failed for user {} / {}-{}: {}",
                    userId, year, month, e.getMessage());
        }
    }

    /**
     * Wave 4 (item 8): evaluate every active saving project's pace against its deadline and maybe
     * ask for a catch-up contribution (INCREASE_CONTRIBUTION) on each project that has fallen
     * behind. Off-track = the required monthly rate (remaining ÷ months to deadline) exceeds the
     * observed contribution rate over the trailing closed months. WARN by default; a stalled
     * project (nothing contributed in the whole window) or a ≥2× shortfall escalates to CRITICAL,
     * so HARD users only hear about projects in real trouble. Best-effort, never throws.
     */
    public void maybeProduceProjectHealth(UUID userId, LocalDate asOf) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            Severity gate = pushGateFor(user == null ? null : user.getInterventionLevel());
            if (gate == null) {
                return; // pull-only / unset → no push
            }

            YearMonth period = YearMonth.from(asOf);
            List<Project> projects = projectRepository.findAllByUser(new UserId(userId));

            // OVER_COMMITTED is capacity-driven, not funding-driven — it runs regardless of the item-1
            // auto-invest suppression. It matters MORE when settlement is on and silently capping the
            // excess saving, so suppressing it there would hide exactly the state it exists to surface.
            // The ACTIVE/positive-monthly filter is shared by the detector and the candidate builder.
            List<Project> overCommitCandidates = overCommitmentProjects(projects);
            Insight overCommitted = overCommittedInsight(user, overCommitCandidates, period);
            if (overCommitted != null && overCommitted.getSeverity().atLeast(gate)) {
                persistAndPush(userId, rebalanceProjectsCandidate(overCommitted, user, overCommitCandidates, period));
            }

            // The per-project "contribute more" nudges assume manual funding — suppressed when
            // settlement auto-invests surplus (item 1) or the user is TRACK_ONLY (item 6). OVER_COMMITTED
            // above is intentionally exempt: rebalancing down fits passive tracking.
            if (suppressContributionNudges(user)) {
                return;
            }
            for (Project stub : projects) {
                // findAllByUser omits contributions, which offTrackInsight needs for the observed rate
                // AND remaining; re-fetch the full aggregate (weekly batch — the N+1 cost is fine).
                // Without this every eligible project reads 0 observed → a bogus CRITICAL every week.
                Project project = projectRepository.findById(stub.getId()).orElse(stub);
                Insight insight = offTrackInsight(project, asOf);
                if (insight == null || !insight.getSeverity().atLeast(gate)) {
                    continue;
                }
                persistAndPush(userId, increaseContributionCandidate(insight, project, period));
            }
        } catch (Exception e) {
            log.warn("[Suggestion] project-health producer failed for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Wave 1: on an expense create, synchronously check the just-saved transaction for two
     * per-transaction anomalies — a {@code LARGE_TRANSACTION} outlier and a {@code DUPLICATE_CHARGE} —
     * and push an informational notification for each (no suggestion, no accept action). Gated by
     * push presence (any non-NOTIFY user), best-effort, never throws. Immediacy is the point, so it
     * runs on the create path rather than a schedule.
     */
    public void maybeNotifyTransactionAnomalies(UUID userId, Transaction saved) {
        try {
            if (saved == null || saved.getType() != TransactionType.EXPENSE || saved.getDate() == null) {
                return;
            }
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || pushGateFor(user.getInterventionLevel()) == null) {
                return; // pull-only / unset → no push
            }

            LocalDate asOf = saved.getDate().toLocalDate();
            List<Transaction> history = loadHistory(userId, asOf, YearMonth.from(asOf));

            analyzer.detectDuplicateCharge(history, saved).ifPresent(insight ->
                    notifyInsight(userId, insight, "notification.insight.duplicate_charge|" + insight.getCategory().name()));
            analyzer.detectLargeTransaction(history, saved).ifPresent(insight ->
                    notifyInsight(userId, insight, "notification.insight.large_transaction|" + insight.getCategory().name()));
        } catch (Exception e) {
            log.warn("[Suggestion] transaction-anomaly notify failed for user {}: {}", userId, e.getMessage());
        }
    }

    /** Push a notification-only insight (no suggestion card), narrated when available. */
    private void notifyInsight(UUID userId, Insight insight, String fallbackCode) {
        createNotificationUseCase.createNotification(userId, pushText(insight, fallbackCode), "app://transactions");
    }

    /**
     * Wave 4 (item 10): the user has savings capacity but no active project to receive it — ask
     * whether to start one. Persisted as a navigate-only CREATE_PROJECT suggestion (see
     * {@link #createProjectCandidate}) rather than a bare notification, so it lives in the in-app
     * list and inherits dedup, cooldown and dismissal-backoff. The seed (pace-scaled amount) rides
     * in the payload for the FE to pre-fill the create form on accept.
     */
    private void maybeNudgeCreateProject(UUID userId, User user, Insight capacity,
                                         BigDecimal disposable, YearMonth period) throws Exception {
        // The seed is a MONTHLY figure (pace-scaled disposable). Clamp it to 20% of income so the
        // target we derive from it doesn't breach the MonthlyObligationPolicy cap when the user submits
        // the pre-filled form (income is the available proxy for usableIncome, the policy's true basis).
        BigDecimal monthly = scaleByPace(disposable, user.getSavingPace()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal income = user.getIncome();
        if (income != null && income.compareTo(BigDecimal.ZERO) > 0) {
            monthly = monthly.min(income.multiply(MONTHLY_OBLIGATION_RATE).setScale(2, RoundingMode.DOWN));
        }
        if (monthly.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // A saving goal (target + deadline) is ultimately the user's intent; we can only seed a sensible,
        // fully-editable draft from the one thing we know — the sustainable monthly. Over a default
        // horizon: target = monthly × horizon, deadline = horizon months out. The deadline lands a month
        // past that many contributions, so the project's derived monthly (target ÷ monthsToDeadline, which
        // counts the current partial month) sits just UNDER the clamped monthly — never above it, keeping
        // the seeded draft safely inside the 20% cap.
        BigDecimal target = monthly.multiply(BigDecimal.valueOf(CREATE_PROJECT_HORIZON_MONTHS))
                .setScale(2, RoundingMode.HALF_UP);
        LocalDate deadline = LocalDate.now(clock).plusMonths(CREATE_PROJECT_HORIZON_MONTHS);

        persistAndPush(userId, createProjectCandidate(capacity, target, monthly, deadline, period));
    }

    /**
     * CREATE_PROJECT: SAVINGS_CAPACITY headroom with no active project to receive it. Navigate-only
     * (like REVIEW_SUBSCRIPTION) — accept executes nothing server-side; the payload seeds the FE's
     * pre-filled create form ({@code resolvedValue} = target total, plus the suggested monthly and
     * deadline). INFO by nature, so it isn't gated by severity (the caller already confirmed the user
     * gets pushes), mirroring the savings nudge.
     */
    private Candidate createProjectCandidate(Insight capacity, BigDecimal target, BigDecimal monthly,
                                             LocalDate deadline, YearMonth period) {
        SuggestionPayload.ProposedAction action = SuggestionPayload.ProposedAction.builder()
                .resolvedValue(target)   // seeded target total — the create form's primary amount field
                .monthlySaving(monthly)  // suggested monthly (for display / the FE's monthly field)
                .deadline(deadline)      // suggested deadline (default horizon out)
                .build();

        return candidate(SuggestionType.CREATE_PROJECT, capacity, action,
                "CREATE_PROJECT:" + period,
                "notification.suggestion.create_project");
    }

    // ---- candidate builders --------------------------------------------------------------

    /** RAISE_BUDGET: BUDGET_PACE overshoot on a category that already has a budget. */
    private Candidate raiseBudgetCandidate(List<Insight> insights, Category category, Budget budget,
                                           Severity gate, List<Transaction> history, YearMonth period,
                                           BigDecimal income) {
        Insight insight = bestQualifying(insights, InsightType.BUDGET_PACE, category, gate);
        if (insight == null) {
            return null;
        }
        BigDecimal resolvedValue = capByIncome(
                analyzer.trailing3MonthMedian(history, category, period)
                        .max(toBigDecimal(insight.getMetrics().get("projectedEndOfMonth"))),
                income);
        // After capping, a "raise" that isn't above the current limit is meaningless — don't ask.
        if (resolvedValue.compareTo(budget.getAmountLimit()) <= 0) {
            return null;
        }

        SuggestionPayload.ProposedAction action = SuggestionPayload.ProposedAction.builder()
                .category(category)
                .budgetId(budget.getId())
                .resolvedValue(resolvedValue)
                .build();

        return candidate(SuggestionType.RAISE_BUDGET, insight, action,
                "RAISE_BUDGET:" + category.name() + ":" + period,
                "notification.suggestion.raise_budget|" + category.name());
    }

    /**
     * REALLOCATE_BUDGET: a budgeted category on pace to overshoot (BUDGET_PACE), funded by pulling
     * headroom from other budgets the same month that are confirmed underspent (BUDGET_UNDERSPEND).
     * Zero-sum — the committed total is unchanged, so it never trips BUDGET_OVER_INCOME. Returns
     * null (so the caller falls back to RAISE_BUDGET) when the underspent donors can't <em>fully</em>
     * fund the gap; a strict full-cover keeps the card honest.
     */
    private Candidate reallocateBudgetCandidate(List<Insight> insights, Category category, Budget recipient,
                                                Severity gate, List<Budget> budgets, YearMonth period) {
        Insight pace = bestQualifying(insights, InsightType.BUDGET_PACE, category, gate);
        if (pace == null) {
            return null;
        }
        BigDecimal currentLimit = recipient.getAmountLimit();
        // How much more the recipient needs so it doesn't finish over its limit, rounded up to the cent.
        BigDecimal gap = toBigDecimal(pace.getMetrics().get("projectedEndOfMonth"))
                .subtract(currentLimit).setScale(2, RoundingMode.CEILING);
        if (gap.compareTo(MIN_MOVE) < 0) {
            return null; // trivial overshoot — not worth an ask
        }

        // Donors: every OTHER budget this month carrying a BUDGET_UNDERSPEND insight. That insight
        // only fires when the trailing median AND the current pace both sit well under the limit, so
        // the budget is safe to borrow from. Available headroom is floored at what the donor has
        // spent or is on pace to spend, so a donor can never be left born-exceeded or starved.
        List<Donor> donors = new ArrayList<>();
        BigDecimal totalAvailable = BigDecimal.ZERO;
        for (Budget b : budgets) {
            if (b == null || b.getId() == null || b.getAmountLimit() == null
                    || b.getId().equals(recipient.getId())) {
                continue;
            }
            Insight underspend = insights.stream()
                    .filter(i -> i.getType() == InsightType.BUDGET_UNDERSPEND)
                    .filter(i -> i.getCategory() == b.getCategory())
                    .findFirst().orElse(null);
            if (underspend == null) {
                continue;
            }
            BigDecimal spent = b.getSpent() != null ? b.getSpent() : BigDecimal.ZERO;
            BigDecimal floor = spent.max(toBigDecimal(underspend.getMetrics().get("projectedEndOfMonth")));
            BigDecimal available = b.getAmountLimit().subtract(floor).max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.DOWN);
            if (available.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            donors.add(new Donor(b, available));
            totalAvailable = totalAvailable.add(available);
        }
        if (totalAvailable.compareTo(gap) < 0) {
            return null; // donors can't fully fund the move — let RAISE_BUDGET handle it
        }

        // Take from each donor proportionally to its headroom (rounded DOWN), then hand the rounding
        // remainder to donors with spare room, largest first, so the moved total equals the gap exactly.
        donors.sort(Comparator.comparing(Donor::available).reversed());
        BigDecimal[] takes = new BigDecimal[donors.size()];
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < donors.size(); i++) {
            BigDecimal take = gap.multiply(donors.get(i).available())
                    .divide(totalAvailable, 2, RoundingMode.DOWN)
                    .min(donors.get(i).available());
            takes[i] = take;
            allocated = allocated.add(take);
        }
        BigDecimal remainder = gap.subtract(allocated);
        for (int i = 0; i < donors.size() && remainder.compareTo(BigDecimal.ZERO) > 0; i++) {
            BigDecimal add = donors.get(i).available().subtract(takes[i]).min(remainder);
            takes[i] = takes[i].add(add);
            remainder = remainder.subtract(add);
        }
        if (remainder.compareTo(BigDecimal.ZERO) > 0) {
            return null; // couldn't place the full gap (shouldn't happen given the feasibility check)
        }

        List<SuggestionPayload.BudgetAdjustment> adjustments = new ArrayList<>();
        adjustments.add(SuggestionPayload.BudgetAdjustment.builder()
                .budgetId(recipient.getId())
                .category(category)
                .currentLimit(currentLimit)
                .newLimit(currentLimit.add(gap)) // recipient absorbs the whole move
                .build());
        for (int i = 0; i < donors.size(); i++) {
            if (takes[i].compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Budget d = donors.get(i).budget();
            adjustments.add(SuggestionPayload.BudgetAdjustment.builder()
                    .budgetId(d.getId())
                    .category(d.getCategory())
                    .currentLimit(d.getAmountLimit())
                    .newLimit(d.getAmountLimit().subtract(takes[i]))
                    .build());
        }

        SuggestionPayload.ProposedAction action = SuggestionPayload.ProposedAction.builder()
                .category(category)
                .month(period.getMonthValue())
                .year(period.getYear())
                .resolvedValue(gap) // the amount moved into the recipient, for display
                .budgetAdjustments(adjustments)
                .build();

        return candidate(SuggestionType.REALLOCATE_BUDGET, pace, action,
                "REALLOCATE_BUDGET:" + category.name() + ":" + period,
                "notification.suggestion.reallocate_budget|" + category.name());
    }

    /** A budget with spare headroom that can donate to a reallocation, and how much it can give. */
    private record Donor(Budget budget, BigDecimal available) { }

    /** CREATE_BUDGET: OVERSPEND_ANOMALY on a category with no budget to anchor spending. */
    private Candidate createBudgetCandidate(List<Insight> insights, Category category,
                                            Severity gate, List<Transaction> history, YearMonth period,
                                            BigDecimal income) {
        Insight insight = bestQualifying(insights, InsightType.OVERSPEND_ANOMALY, category, gate);
        if (insight == null) {
            return null;
        }
        // Anchor on the historical norm, but never below what's already spent this month: CreateBudget
        // backfills spent from the month's existing expenses, so a limit under the overspend that
        // triggered this would be born already-EXCEEDED. The current-month total is carried on the
        // anomaly insight as "actual" (SpendingInsightAnalyzer#detectOverspendAnomalies).
        BigDecimal actualThisMonth = toBigDecimal(insight.getMetrics().get("actual"));
        BigDecimal resolvedValue = capByIncome(
                analyzer.trailing3MonthMedian(history, category, period), income)
                .max(actualThisMonth);
        if (resolvedValue.compareTo(BigDecimal.ZERO) <= 0) {
            return null; // no robust baseline to propose a limit from
        }

        SuggestionPayload.ProposedAction action = SuggestionPayload.ProposedAction.builder()
                .category(category)
                .month(period.getMonthValue())
                .year(period.getYear())
                .resolvedValue(resolvedValue)
                .build();

        return candidate(SuggestionType.CREATE_BUDGET, insight, action,
                "CREATE_BUDGET:" + category.name() + ":" + period,
                "notification.suggestion.create_budget|" + category.name());
    }

    /** SET_CATEGORY_LIMIT: CATEGORY_TREND on a budgetless category — set a first anchoring limit. */
    private Candidate setCategoryLimitCandidate(List<Insight> insights, Category category, Severity gate,
                                                List<Transaction> history, YearMonth period, BigDecimal income) {
        Insight insight = bestQualifying(insights, InsightType.CATEGORY_TREND, category, gate);
        if (insight == null) {
            return null;
        }
        // Anchor the first limit on the trailing norm (robust to a one-off), capped by income —
        // but never below what's already spent this month. CATEGORY_TREND fires precisely because
        // the category is trending UP, so the current month usually sits above the prior-3-month
        // median; without this floor CreateBudget would backfill that spend onto a lower limit and
        // the budget would be born already-EXCEEDED (negative remaining). The floor wins even over
        // the income cap, mirroring createBudgetCandidate.
        BigDecimal resolvedValue = capByIncome(analyzer.trailing3MonthMedian(history, category, period), income)
                .max(analyzer.currentMonthTotal(history, category, period));
        if (resolvedValue.compareTo(BigDecimal.ZERO) <= 0) {
            return null; // no robust baseline to propose a limit from
        }

        SuggestionPayload.ProposedAction action = SuggestionPayload.ProposedAction.builder()
                .category(category)
                .month(period.getMonthValue())
                .year(period.getYear())
                .resolvedValue(resolvedValue)
                .build();

        return candidate(SuggestionType.SET_CATEGORY_LIMIT, insight, action,
                "SET_CATEGORY_LIMIT:" + category.name() + ":" + period,
                "notification.suggestion.set_category_limit|" + category.name());
    }

    /**
     * REDUCE_BUDGET: BUDGET_UNDERSPEND on a budgeted category. INFO by nature, so it isn't gated by
     * severity (the caller already confirmed the user gets pushes) — it mirrors the savings nudge.
     */
    private Candidate reduceBudgetCandidate(List<Insight> insights, Category category, Budget budget, YearMonth period) {
        Insight insight = insights.stream()
                .filter(i -> i.getType() == InsightType.BUDGET_UNDERSPEND)
                .filter(i -> i.getCategory() == category)
                .findFirst().orElse(null);
        if (insight == null) {
            return null;
        }
        // Trim to the median actual plus a little headroom, so the tighter limit still breathes.
        BigDecimal resolvedValue = toBigDecimal(insight.getMetrics().get("medianActual"))
                .multiply(REDUCE_HEADROOM).setScale(2, RoundingMode.HALF_UP);
        // A "reduce" that isn't below the current limit is meaningless — don't ask.
        if (resolvedValue.compareTo(BigDecimal.ZERO) <= 0 || resolvedValue.compareTo(budget.getAmountLimit()) >= 0) {
            return null;
        }

        SuggestionPayload.ProposedAction action = SuggestionPayload.ProposedAction.builder()
                .category(category)
                .budgetId(budget.getId())
                .resolvedValue(resolvedValue)
                .build();

        return candidate(SuggestionType.REDUCE_BUDGET, insight, action,
                "REDUCE_BUDGET:" + category.name() + ":" + period,
                "notification.suggestion.reduce_budget|" + category.name());
    }

    /** CONTRIBUTE_TO_PROJECT: SAVINGS_CAPACITY headroom toward a specific active project. */
    private Candidate contributeCandidate(Insight insight, Project target, BigDecimal amount, YearMonth period) {
        SuggestionPayload.ProposedAction action = SuggestionPayload.ProposedAction.builder()
                .projectId(target.getId().value())
                .resolvedValue(amount)
                .build();

        return candidate(SuggestionType.CONTRIBUTE_TO_PROJECT, insight, action,
                "CONTRIBUTE_TO_PROJECT:" + target.getId().value() + ":" + period,
                "notification.suggestion.contribute_to_project|" + target.getName());
    }

    /**
     * REBALANCE_BUDGETS: BUDGET_OVER_INCOME on the month — propose scaling every budget's limit by
     * income ÷ committed so the total fits the envelope. Per-budget limits are rounded DOWN to the
     * cent so the rebalanced total can never exceed income. The full per-budget snapshot rides in
     * the payload: consent is to those concrete numbers, not a recompute recipe.
     */
    private Candidate rebalanceCandidate(Insight insight, List<Budget> budgets, BigDecimal income, YearMonth period) {
        BigDecimal committed = budgets.stream()
                .filter(b -> b != null && b.getAmountLimit() != null)
                .map(Budget::getAmountLimit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (committed.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal factor = income.divide(committed, 6, RoundingMode.DOWN);

        List<SuggestionPayload.BudgetAdjustment> adjustments = new ArrayList<>();
        BigDecimal newTotal = BigDecimal.ZERO;
        for (Budget budget : budgets) {
            if (budget == null || budget.getId() == null || budget.getAmountLimit() == null
                    || budget.getAmountLimit().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal newLimit = budget.getAmountLimit().multiply(factor)
                    .setScale(2, RoundingMode.DOWN)
                    .max(MIN_LIMIT); // a limit scaled below one cent is meaningless
            if (newLimit.compareTo(budget.getAmountLimit()) >= 0) {
                continue; // not a reduction (tiny limit floored at the minimum) — nothing to consent to
            }
            adjustments.add(SuggestionPayload.BudgetAdjustment.builder()
                    .budgetId(budget.getId())
                    .category(budget.getCategory())
                    .currentLimit(budget.getAmountLimit())
                    .newLimit(newLimit)
                    .build());
            newTotal = newTotal.add(newLimit);
        }
        if (adjustments.isEmpty()) {
            return null;
        }

        SuggestionPayload.ProposedAction action = SuggestionPayload.ProposedAction.builder()
                .month(period.getMonthValue())
                .year(period.getYear())
                .resolvedValue(newTotal) // the new committed total, for display
                .budgetAdjustments(adjustments)
                .build();

        return candidate(SuggestionType.REBALANCE_BUDGETS, insight, action,
                "REBALANCE_BUDGETS:" + period,
                "notification.suggestion.rebalance_budgets");
    }

    /**
     * PROJECT_OFF_TRACK evaluation for one project (Wave 4 item 8). Pure math over the already-
     * loaded aggregate — no I/O. Judged only for live, fundable projects that are at least one
     * month old (a fresh project hasn't had a chance to be "behind" yet):
     * <ul>
     *   <li>required rate = remaining ÷ months left (deadline month counts, current partial month
     *       counts — the money can still move in both);</li>
     *   <li>observed rate = contributions over the trailing closed months (up to
     *       {@value #OFF_TRACK_OBSERVE_MONTHS}, less for younger projects) ÷ that window. The
     *       current partial month is excluded — it isn't over, so it can't be under-delivered.</li>
     * </ul>
     */
    private Insight offTrackInsight(Project project, LocalDate asOf) {
        if (project == null || project.getStatus() != ProjectStatus.ACTIVE || project.isSubPersonal()) {
            return null;
        }
        BigDecimal remaining = project.remaining().amount();
        if (remaining.compareTo(BigDecimal.ZERO) <= 0 || project.getDeadline() == null
                || project.getDeadline().isBefore(asOf)) {
            return null; // funded, or past deadline (lazy expiry handles that elsewhere)
        }

        YearMonth current = YearMonth.from(asOf);
        YearMonth createdMonth = YearMonth.from(LocalDate.ofInstant(project.getCreatedAt(), clock.getZone()));
        long ageMonths = ChronoUnit.MONTHS.between(createdMonth, current);
        if (ageMonths < 1) {
            return null; // give a new project a full month before judging its pace
        }

        long monthsLeft = ChronoUnit.MONTHS.between(current, YearMonth.from(project.getDeadline())) + 1;
        BigDecimal requiredMonthly = remaining.divide(BigDecimal.valueOf(monthsLeft), 2, RoundingMode.CEILING);

        int windowMonths = (int) Math.min(OFF_TRACK_OBSERVE_MONTHS, ageMonths);
        Instant windowStart = current.minusMonths(windowMonths).atDay(1).atStartOfDay(clock.getZone()).toInstant();
        Instant windowEnd = current.atDay(1).atStartOfDay(clock.getZone()).toInstant();
        BigDecimal observedTotal = project.getContributions().stream()
                .filter(c -> !c.getCreatedAt().isBefore(windowStart) && c.getCreatedAt().isBefore(windowEnd))
                .map(c -> c.getMoney().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal observedMonthly = observedTotal.divide(BigDecimal.valueOf(windowMonths), 2, RoundingMode.HALF_UP);

        // Item 3 (docs/settlement-insights-followup.md): settlement holds the authoritative "behind"
        // signal. When it has accrued arrears (moneyOwed > 0) on this project, that overrides the
        // re-derived pace heuristic — a debt the engine actually booked is a stronger, truer off-track
        // signal than observed-vs-required, and re-deriving risks the two systems disagreeing. Fall
        // back to the heuristic only when settlement has said nothing (engine off, or no debt).
        ProjectTracking tracking = project.getTracking();
        BigDecimal moneyOwed = tracking != null && tracking.getMoneyOwed() != null
                ? tracking.getMoneyOwed() : BigDecimal.ZERO;
        int frozenMonths = tracking != null ? tracking.getFrozenMonths() : 0;
        boolean settlementArrears = moneyOwed.compareTo(BigDecimal.ZERO) > 0;

        boolean critical;
        if (settlementArrears) {
            // Deep arrears (owe ≥ 2× the required monthly) or months already frozen = real trouble.
            critical = frozenMonths > 0
                    || moneyOwed.compareTo(requiredMonthly.multiply(OFF_TRACK_CRITICAL_RATIO)) >= 0;
        } else {
            if (requiredMonthly.compareTo(observedMonthly) <= 0) {
                return null; // on pace, and settlement has no debt to report
            }
            // Stalled (nothing in the whole window) or needing ≥2× the observed pace = real trouble.
            critical = observedMonthly.compareTo(BigDecimal.ZERO) == 0
                    || requiredMonthly.compareTo(observedMonthly.multiply(OFF_TRACK_CRITICAL_RATIO)) >= 0;
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("projectName", project.getName());
        metrics.put("remaining", remaining);
        metrics.put("deadline", project.getDeadline().toString());
        metrics.put("monthsLeft", monthsLeft);
        metrics.put("requiredMonthly", requiredMonthly);
        metrics.put("observedMonthly", observedMonthly);
        metrics.put("shortfall", requiredMonthly.subtract(observedMonthly));
        metrics.put("observedWindowMonths", windowMonths);
        metrics.put("moneyOwed", moneyOwed);                                  // settlement arrears (0 when clear/off)
        metrics.put("source", settlementArrears ? "settlement" : "heuristic"); // which signal fired

        return Insight.builder()
                .type(InsightType.PROJECT_OFF_TRACK)
                .category(null)
                .severity(critical ? Severity.CRITICAL : Severity.WARN)
                .period(current)
                .metrics(metrics)
                .code("insight.project_off_track")
                .build();
    }

    /**
     * INCREASE_CONTRIBUTION: PROJECT_OFF_TRACK on a specific project — ask to contribute the
     * required monthly catch-up amount (never more than the project still needs). Reuses the
     * CONTRIBUTE_TO_PROJECT executor shape: {@code projectId} + {@code resolvedValue}.
     */
    private Candidate increaseContributionCandidate(Insight insight, Project project, YearMonth period) {
        BigDecimal amount = toBigDecimal(insight.getMetrics().get("requiredMonthly"))
                .min(project.remaining().amount())
                .setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        SuggestionPayload.ProposedAction action = SuggestionPayload.ProposedAction.builder()
                .projectId(project.getId().value())
                .resolvedValue(amount)
                .build();

        return candidate(SuggestionType.INCREASE_CONTRIBUTION, insight, action,
                "INCREASE_CONTRIBUTION:" + project.getId().value() + ":" + period,
                "notification.suggestion.increase_contribution|" + project.getName());
    }

    /** ACTIVE, non-group projects with a positive monthly saving target — the rebalance candidates. */
    private List<Project> overCommitmentProjects(List<Project> projects) {
        return projects.stream()
                .filter(p -> p != null && p.getStatus() == ProjectStatus.ACTIVE && !p.isSubPersonal())
                .filter(p -> p.getTracking() != null && p.getTracking().getMonthlySaving() != null
                        && p.getTracking().getMonthlySaving().compareTo(BigDecimal.ZERO) > 0)
                .toList();
    }

    /**
     * OVER_COMMITTED (item 4): the combined monthly saving of the user's ACTIVE projects exceeds their
     * pace capacity ({@code User.getSavingCapacity()} = income × pace — the item-2 authoritative
     * figure). Settlement silently caps the excess (revival Step 1), so without this the user just
     * quietly under-saves. Pure math over the already-filtered candidate list — no I/O.
     */
    private Insight overCommittedInsight(User user, List<Project> active, YearMonth period) {
        if (user == null) {
            return null;
        }
        BigDecimal capacity = user.getSavingCapacity();
        if (capacity.compareTo(BigDecimal.ZERO) <= 0) {
            return null; // no income/pace configured → can't judge over-commitment
        }
        BigDecimal committed = active.stream()
                .map(p -> p.getTracking().getMonthlySaving())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (committed.compareTo(capacity) <= 0) {
            return null; // within pace capacity — nothing to surface
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("capacity", capacity);
        metrics.put("committedMonthly", committed);
        metrics.put("excess", committed.subtract(capacity));
        metrics.put("projectCount", active.size());

        return Insight.builder()
                .type(InsightType.OVER_COMMITTED)
                .category(null)
                .severity(Severity.WARN)
                .period(period)
                .metrics(metrics)
                .code("insight.over_committed")
                .build();
    }

    /**
     * REBALANCE_PROJECTS: scale every over-committed project's monthly saving down to fit capacity by
     * extending its deadline (monthly = remaining ÷ monthsLeft, so stretching the horizon lowers the
     * monthly). Consent is to the concrete per-project new deadlines snapshotted here; accept applies
     * them verbatim. Uses only loaded tracking fields (monthlySaving, monthLeft), so it never depends
     * on contributions the project list doesn't carry.
     */
    private Candidate rebalanceProjectsCandidate(Insight insight, User user, List<Project> active, YearMonth period) {
        BigDecimal capacity = user.getSavingCapacity();
        BigDecimal committed = active.stream()
                .map(p -> p.getTracking().getMonthlySaving())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (capacity.compareTo(BigDecimal.ZERO) <= 0 || committed.compareTo(capacity) <= 0) {
            return null;
        }
        // factor < 1: the fraction of its current pace each project keeps so the total fits capacity.
        BigDecimal factor = capacity.divide(committed, 6, RoundingMode.DOWN);
        LocalDate today = LocalDate.now(clock);

        List<SuggestionPayload.ProjectAdjustment> adjustments = new ArrayList<>();
        for (Project p : active) {
            ProjectTracking t = p.getTracking();
            int monthsLeft = t.getMonthLeft();
            if (monthsLeft < 1) {
                continue; // already at/past its horizon — extending it is meaningless
            }
            BigDecimal monthly = t.getMonthlySaving();
            BigDecimal newMonthly = monthly.multiply(factor).setScale(2, RoundingMode.CEILING).max(MIN_LIMIT);
            // newMonthsLeft = ceil(monthsLeft ÷ factor): the same remaining saved at the slower pace.
            int newMonthsLeft = (int) Math.min(60, Math.ceil(monthsLeft / factor.doubleValue()));
            if (newMonthsLeft <= monthsLeft) {
                continue; // capped at the 60-month ceiling with no room to extend — no relief here
            }
            LocalDate newDeadline = today.plusMonths(newMonthsLeft - 1L);
            if (p.getDeadline() != null && !newDeadline.isAfter(p.getDeadline())) {
                continue; // the computed deadline doesn't actually extend this project
            }
            adjustments.add(SuggestionPayload.ProjectAdjustment.builder()
                    .projectId(p.getId().value())
                    .projectName(p.getName())
                    .currentMonthlySaving(monthly)
                    .newMonthlySaving(newMonthly)
                    .currentDeadline(p.getDeadline())
                    .newDeadline(newDeadline)
                    .build());
        }
        if (adjustments.isEmpty()) {
            return null; // nothing extendable (e.g. every project already at the 60-month ceiling)
        }

        SuggestionPayload.ProposedAction action = SuggestionPayload.ProposedAction.builder()
                .resolvedValue(capacity) // the target combined monthly, for display
                .projectAdjustments(adjustments)
                .build();

        return candidate(SuggestionType.REBALANCE_PROJECTS, insight, action,
                "REBALANCE_PROJECTS:" + period,
                "notification.suggestion.rebalance_projects");
    }

    /**
     * REVIEW_SUBSCRIPTION: SUBSCRIPTION_PRICE_HIKE on the affected category. Acknowledge-only —
     * accepting marks the hike reviewed and executes nothing (the real action happens outside the
     * app), so the payload carries no proposed action; the card renders from the insight snapshot.
     * The subscription's description is part of the dedup key so two different subscriptions in
     * one category are separate topics.
     */
    private Candidate reviewSubscriptionCandidate(List<Insight> insights, Category category,
                                                  Severity gate, YearMonth period) {
        Insight insight = bestQualifying(insights, InsightType.SUBSCRIPTION_PRICE_HIKE, category, gate);
        if (insight == null) {
            return null;
        }
        String description = String.valueOf(insight.getMetrics().getOrDefault("description", ""));

        return candidate(SuggestionType.REVIEW_SUBSCRIPTION, insight, null,
                "REVIEW_SUBSCRIPTION:" + category.name() + ":" + slug(description) + ":" + period,
                "notification.suggestion.review_subscription|" + description);
    }

    /** Compact, key-safe form of a free-text description for use inside a dedup key. */
    private static String slug(String text) {
        String slug = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.length() > 40 ? slug.substring(0, 40) : slug;
    }

    // ---- shared persist + push -----------------------------------------------------------

    private void persistAndPush(UUID userId, Candidate candidate) throws Exception {
        if (candidate == null) {
            return;
        }
        // Pre-check: one open ask per topic. The partial-unique index is the hard backstop.
        if (suggestionRepository.findOpenByUserIdAndDedupKey(userId, candidate.dedupKey).isPresent()) {
            log.debug("[Suggestion] skip — open ask already exists for {}", candidate.dedupKey);
            return;
        }
        // Fatigue ceiling. A CRITICAL escalation bypasses all of it. For non-CRITICAL asks:
        //  - dismissal suppression and the per-topic cooldown fully skip the ask;
        //  - the daily cap only DEMOTES the push — the card is still created (so it stays actionable
        //    in the in-app list) but a single daily roll-up ping replaces the per-suggestion push,
        //    so nothing is silently lost. See docs/redesign-phase2-3-plan.md §P3-A.
        boolean overflow = false;           // over the daily cap → create the card, no individual push
        boolean firstOverflowToday = false; // only the first crossing sends the one roll-up ping
        if (candidate.severity != Severity.CRITICAL) {
            Instant now = clock.instant();
            SuggestionTopicStat stat = topicStatRepository.find(userId, candidate.dedupKey).orElse(null);
            if (isSuppressed(stat)) {
                log.debug("[Suggestion] suppressed (dismissal fatigue) for {}", candidate.dedupKey);
                return;
            }
            if (isInCooldown(stat, now)) {
                log.debug("[Suggestion] in cooldown ({}h) for {}", fatigue.getCooldownHours(), candidate.dedupKey);
                return;
            }
            int cap = fatigue.getDailyCap();
            if (cap > 0) {
                int used = suggestionRepository.countTowardDailyCap(userId, startOfDay(now));
                if (used >= cap) {
                    overflow = true;
                    firstOverflowToday = (used == cap); // exactly at the boundary = first over today
                }
            }
        }

        Suggestion suggestion = Suggestion.create(
                userId, candidate.type, candidate.payloadJson, candidate.dedupKey, null);

        Suggestion saved;
        try {
            saved = suggestionRepository.save(suggestion);
        } catch (Exception insertEx) {
            // A concurrent insert won the dedup race — treat as already asked.
            log.debug("[Suggestion] dedup insert conflict for {}: {}", candidate.dedupKey, insertEx.getMessage());
            return;
        }

        if (overflow) {
            // Daily-cap overflow: the card is in the in-app list; don't ping per-suggestion. Send one
            // roll-up notification per day (the digest pattern) so the user knows there's more to review.
            if (firstOverflowToday) {
                createNotificationUseCase.createNotification(
                        userId, "notification.digest.pending_suggestions", "app://suggestions");
            }
            log.info("[Suggestion] {} id={} created without push (daily cap {} reached — rolled into digest)",
                    candidate.type, saved.getId(), fatigue.getDailyCap());
            return;
        }

        createNotificationUseCase.createNotification(
                userId, candidate.notificationContent, "app://suggestions/" + saved.getId());
        recordPush(userId, candidate.dedupKey); // P3-A: stamp the topic so the cooldown can space repeats

        log.info("[Suggestion] raised {} id={} for user={} dedupKey={}",
                candidate.type, saved.getId(), userId, candidate.dedupKey);
    }

    /** True when the topic has been dismissed enough times to back off (CRITICAL handled by caller). */
    private boolean isSuppressed(SuggestionTopicStat stat) {
        return stat != null && stat.getDismissCount() >= SUPPRESS_AFTER_DISMISSALS;
    }

    /** P3-A: still inside the per-topic cooldown window since the last push? */
    private boolean isInCooldown(SuggestionTopicStat stat, Instant now) {
        if (stat == null || stat.getLastPushedAt() == null || fatigue.getCooldownHours() <= 0) {
            return false;
        }
        Instant readyAt = stat.getLastPushedAt().plus(Duration.ofHours(fatigue.getCooldownHours()));
        return now.isBefore(readyAt);
    }

    /** Start-of-day instant in the producer's clock zone — the window for the P3-A daily cap. */
    private Instant startOfDay(Instant now) {
        return LocalDate.ofInstant(now, clock.getZone()).atStartOfDay(clock.getZone()).toInstant();
    }

    /** Best-effort: stamp the topic's last-pushed time so the P3-A cooldown can space out repeats. */
    private void recordPush(UUID userId, String dedupKey) {
        try {
            SuggestionTopicStat stat = topicStatRepository.find(userId, dedupKey)
                    .orElseGet(() -> SuggestionTopicStat.start(userId, dedupKey));
            stat.recordPush(clock.instant());
            topicStatRepository.save(stat);
        } catch (Exception e) {
            log.warn("[Suggestion] could not stamp last-pushed for topic {}: {}", dedupKey, e.getMessage());
        }
    }

    // ---- helpers -------------------------------------------------------------------------

    private List<Transaction> loadHistory(UUID userId, LocalDate asOf, YearMonth period) {
        LocalDateTime windowStart = period.minusMonths(SpendingInsightAnalyzer.TRAILING_WINDOW)
                .atDay(1).atStartOfDay();
        LocalDateTime windowEnd = asOf.atTime(23, 59, 59);
        return transactionRepository.findByUserIdWithFilters(
                userId, null, null, windowStart, windowEnd, null, null);
    }

    /** The worst insight of a type+category that clears the push gate, if any. */
    private Insight bestQualifying(List<Insight> insights, InsightType type, Category category, Severity gate) {
        return insights.stream()
                .filter(i -> i.getType() == type)
                .filter(i -> i.getCategory() == category)
                .filter(i -> i.getSeverity().atLeast(gate))
                .max(Comparator.comparingInt(i -> i.getSeverity().ordinal()))
                .orElse(null);
    }

    /** Active, non-group saving project with the nearest deadline that still has room to receive. */
    private Project pickContributionTarget(UUID userId) {
        return projectRepository.findAllByUser(new UserId(userId)).stream()
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE)
                .filter(p -> !p.isSubPersonal())
                .filter(p -> p.remaining().amount().compareTo(BigDecimal.ZERO) > 0)
                .min(Comparator.comparing(Project::getDeadline,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    /** Maps the intervention trait to a push severity gate (null = no push). */
    private Severity pushGateFor(Intervention_Level level) {
        if (level == null) {
            return null;
        }
        return switch (level) {
            case NOTIFY -> null;          // pull-only
            case GENTLE -> Severity.WARN; // WARN/CRITICAL become pushed suggestions
            case HARD -> Severity.CRITICAL;
        };
    }

    /**
     * Sanity ceiling for a proposed budget limit: never suggest a monthly limit larger than the
     * user's stated monthly income — you can't spend money you don't have. When income is unset
     * (cold-start), the value passes through unbounded (the trailing-median anchor still applies).
     */
    private BigDecimal capByIncome(BigDecimal value, BigDecimal income) {
        if (income != null && income.compareTo(BigDecimal.ZERO) > 0) {
            return value.min(income);
        }
        return value;
    }

    /** P2-E: share of disposable to suggest saving, by pace. Cold-start (null) → BALANCED. */
    private BigDecimal scaleByPace(BigDecimal disposable, Saving_Pace pace) {
        BigDecimal fraction = switch (pace == null ? Saving_Pace.BALANCED : pace) {
            case RELAXED -> new BigDecimal("0.10");
            case BALANCED -> new BigDecimal("0.20");
            case AGGRESIVE -> new BigDecimal("0.30");
        };
        return disposable.multiply(fraction);
    }

    /**
     * P3-C: human-readable push body from the insight, best-effort. Falls back to the raw i18n
     * {@code code} when narration is off/unavailable — the FE localizes that key as before.
     */
    private String pushText(Insight insight, String fallbackCode) {
        String narrative = narrator.narrate(insight);
        return narrative != null ? narrative : fallbackCode;
    }

    /**
     * Assemble a fully-resolved {@link Candidate}, narrating the driving insight <b>once</b> and
     * reusing that sentence for both the persisted card ({@code payload.narrative}) and the push
     * body — so the two never diverge, and only one LLM call is made. When narration is
     * off/unavailable the narrative is {@code null} and the push falls back to {@code fallbackCode}
     * (the i18n key the FE localizes), exactly as before.
     */
    private Candidate candidate(SuggestionType type, Insight insight,
                                SuggestionPayload.ProposedAction action,
                                String dedupKey, String fallbackCode) {
        String narrative = narrator.narrate(insight);
        return Candidate.builder()
                .type(type)
                .severity(insight.getSeverity())
                .dedupKey(dedupKey)
                .payloadJson(payloadJson(insight, action, narrative))
                .notificationContent(narrative != null ? narrative : fallbackCode)
                .build();
    }

    private String payloadJson(Insight insight, SuggestionPayload.ProposedAction action, String narrative) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("type", insight.getType().name());
        snapshot.put("severity", insight.getSeverity().name());
        snapshot.put("code", insight.getCode());
        snapshot.put("period", insight.getPeriod() != null ? insight.getPeriod().toString() : null);
        snapshot.put("metrics", insight.getMetrics());

        SuggestionPayload payload = SuggestionPayload.builder()
                .insightSnapshot(snapshot)
                .proposedAction(action)
                .narrative(narrative)
                .build();
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize suggestion payload", e);
        }
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(value.toString());
    }

    /** Internal carrier for a fully-resolved suggestion ready to persist + push. */
    @Builder
    private static class Candidate {
        private final SuggestionType type;
        private final Severity severity;
        private final String dedupKey;
        private final String payloadJson;
        private final String notificationContent;
    }
}
