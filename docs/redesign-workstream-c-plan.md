# Redesign — Workstream C Detailed Plan (Insight + Suggestion round trip)

> Detailed breakdown of Workstream C from `redesign-phase1-plan.md` for later sessions.
> C is the **thesis core**: a deterministic analyzer derives insights, the user pulls them
> in-app, and high-value ones become persisted yes/no `Suggestion`s pushed via the existing
> notification stack. Phase 1 ships the smallest end-to-end slice; Phases 2–3 expand.
> Source of truth for *why*: `redesign-what-and-why.md` §4; for *how*: `redesign-technical-implementation.md` §1–6.
>
> **Status:** Workstreams B ✅, D ✅, A ✅ are done. C is the only remaining Phase-1 workstream.

## 0. Guiding principle (don't violate)

**Compute facts deterministically; only phrase them with the LLM.**
```
transactions / budgets  →  SpendingInsightAnalyzer (pure)  →  Insight[]  →  (optional) LlmPort  →  text
```
The analyzer is a pure domain service — no Spring, no I/O — fully unit-testable. The LLM never
does arithmetic. If Gemini/LLM is down, raw insights still return. Phase 1 ships **without** LLM
narration (deferred to Phase 3); insights carry a stable `code` for front-end i18n instead.

## 1. Reuse map (anchors already in the codebase)

| Need | Existing anchor | Notes |
|---|---|---|
| Category baselines | `TransactionRepository.getExpenseByCategoryBetween`, `getTransactionsByMonth`, `findByUserIdWithFilters` | SQL `GROUP BY` already aggregates monthly/category spend |
| Current budgets | `BudgetRepository.findByUserIdMonthYear(userId, month, year)` / `findByUserIdCategoryMonthYear` | `Budget` has `category`, `amountLimit`, `spent`, `month`, `year`, `calculateProgressPercent`, `AlertLevel` |
| RAISE_BUDGET action | `UpdateBudgetUseCase.execute(budgetId, userId, UpdateBudgetRequest{amountLimit, spent})` | the only Phase-1 action target |
| Sync producer hook | `CreateTransactionUseCase.execute(...)` (after expense save + budget `spent` update, ~line 52–66) | where the transaction-create producer fires |
| Push delivery | `CreateNotificationUseCase.createNotification(userId, content, deepLink)` | already persists + best-effort enqueues to Redis (`NotificationQueuePort`); `deepLink` carries the suggestion ref |
| Trait gate | `User.getInterventionLevel()` → `Intervention_Level {NOTIFY, GENTLE, HARD}` | wired in D1; the push severity gate |
| Trait filter | `User.getFocusMode()` → `Focus_Mode {SAVE_MODE, REDUCE_SPENDING, TRACK_ONLY}` | orders/filters which insights surface |
| Latest migration | `V20__add_user_traits.sql` | next is **V21** |

**Don't build:** ML pipeline, clustering, Kafka/event-sourcing, separate analytics DB,
per-transaction realtime recompute, microservice split, generic rules/workflow engine.

## 2. Trait → engine knob (Phase 1 mapping)

`interventionLevel` (redesign `overspendHelp`) sets the **push severity gate**:
- `NOTIFY`  (TELL_ME)    → push gate = **none**; insights are pull-only (in-app screen). No `Suggestion` pushed.
- `GENTLE`  (NUDGE_ME)   → push gate = **WARN**; WARN/CRITICAL insights become pushed `Suggestion`s.
- `HARD`    (HARD_LIMIT) → push gate = **CRITICAL**, immediate on budget cross; max intensity (still a nudge, not enforcement).

`focusMode` (redesign `focus`) filters/orders insight types on the pull screen:
- `REDUCE_SPENDING` (CUT_WASTE) → prioritize `OVERSPEND_ANOMALY`, `BUDGET_PACE`.
- `SAVE_MODE` / `TRACK_ONLY` → lower priority / digest-style (Phase 1: ordering only).

`savingPace` (goalPush) is not central to Phase-1 C (it scales savings amounts — Phase 2).

---

## C1 — `SpendingInsightAnalyzer` (pure domain service)

**Location:** `domain/services/SpendingInsightAnalyzer.java` (alongside `AuthDomainService`).
Pure: `(List<Transaction> history, List<Budget> budgets, YearMonth period, UserTraitsView traits) → List<Insight>`.
No Spring, no repos — the use case loads data and passes it in.

### C1.1 `Insight` value object — `domain/models/insight/Insight.java`
```
Insight {
  InsightType type;        // OVERSPEND_ANOMALY | BUDGET_PACE   (Phase 1 only)
  Category category;       // nullable
  Severity severity;       // INFO | WARN | CRITICAL
  YearMonth period;
  Map<String,Object> metrics;  // structured, NOT prose: {baseline, actual, zScore, projectedEndOfMonth, limit, ...}
  String code;             // stable i18n key, e.g. "insight.overspend_anomaly.food"
}
enum InsightType { OVERSPEND_ANOMALY, BUDGET_PACE }   // CATEGORY_TREND/RECURRING/SAVINGS_CAPACITY = Phase 2+
enum Severity { INFO, WARN, CRITICAL }
```

### C1.2 Method A — overspend anomaly (rolling z-score)
- For each expense `Category`, build trailing-N monthly totals (N = 6, **min 3** months of data).
- `mean`, `stddev` (population) over the trailing window (exclude current month).
- `zScore = (currentMonthActual − mean) / stddev`.
- Severity: `z ≥ 2` → WARN, `z ≥ 3` → CRITICAL, else INFO (only surfaced on pull).
- **Guards:** if `n < 3` or `stddev == 0` → skip or emit INFO only (cold start has no baseline — this is *why* traits exist).
- `metrics = {baseline: mean, stddev, actual, zScore, sampleMonths: n}`.

### C1.3 Method B — budget pace projection (linear)
- For each `Budget` in `period`: `projectedEndOfMonth = spent / dayOfMonth × daysInMonth`.
- If `projectedEndOfMonth > amountLimit`: severity by overshoot ratio
  `r = projected / amountLimit` → `r ≥ 1.0` WARN, `r ≥ 1.25` CRITICAL.
- `metrics = {spent, limit, dayOfMonth, daysInMonth, projectedEndOfMonth, overshootRatio}`.
- All inputs already on `Budget`; trivial + explainable.

### C1.4 Trailing-median helper (for the RAISE_BUDGET resolved value)
- `trailing3MonthMedian(category)` — robust to a single big purchase. Used by C5 to compute the
  proposed new budget limit. Lives in the analyzer (pure) so it's testable.

### C1.5 Tests — `SpendingInsightAnalyzerTest` (the part to defend in the thesis)
z-score: normal spend → no/low insight; 2σ spike → WARN; 3σ → CRITICAL; `n<3` → no anomaly;
`stddev==0` → no divide-by-zero. Pace: under limit → none; on-track-to-exceed → WARN; way over → CRITICAL;
day-1 edge (dayOfMonth=1). Median: odd/even counts, single big outlier ignored.

---

## C2 — `GenerateInsightsUseCase` + pull endpoint

- **`application/usecases/insight/GenerateInsightsUseCase.java`** (`@Transactional(readOnly=true)`):
  load trailing history (`TransactionRepository`), current budgets (`BudgetRepository.findByUserIdMonthYear`),
  load `User` for traits → call analyzer → filter/order by `focusMode` → return `List<Insight>`.
- **DTO:** `InsightResponse` (type, category, severity, period, code, metrics). Keep metrics as a
  generic map so the FE can render without server churn.
- **Endpoint:** `GET /api/v1/insights` (current month) on a new `InsightController`. Pull-only;
  no mutation. This satisfies `NOTIFY` users entirely (no pushes).

## C3 — In-app insights screen (contract only; FE out of scope)

Define and freeze the `InsightResponse` JSON contract in C2. No backend work beyond C2; this
checklist item is just confirming the pull contract is FE-consumable (severity, code for i18n,
metrics for rendering).

---

## C4 — `Suggestion` domain + persistence

### C4.1 Domain — `domain/models/suggestion/Suggestion.java`
```
Suggestion {
  UUID id; UUID userId;
  SuggestionType type;       // RAISE_BUDGET (Phase 1 only)
  SuggestionStatus status;   // PENDING → ACCEPTED | DISMISSED | EXPIRED
  String payloadJson;        // jsonb: { insightSnapshot, proposedAction:{ category, budgetId, resolvedValue } }
  String dedupKey;           // type + category + period, e.g. "RAISE_BUDGET:FOOD:2026-07"
  Instant createdAt, expiresAt, decidedAt;
  // domain methods: accept(), dismiss(), expire() with PENDING-only guards (mirror Project lifecycle style)
}
```
Store the **resolved value**, not a recompute recipe (the user consents to a specific number).

### C4.2 Persistence
- `SuggestionEntity` (`@Table("suggestion")`), `JpaSuggestionRepository`, `SuggestionRepository`
  interface + impl, mapper. `payload` as `jsonb` (Postgres) / map via `@JdbcTypeCode(SqlTypes.JSON)`
  or a `String` column — pick the simplest that passes `ddl-auto: validate`.
- **Migration `V21__add_suggestion.sql`:**
  ```sql
  CREATE TABLE suggestion (
    id          uuid PRIMARY KEY,
    user_id     uuid NOT NULL,
    type        varchar(40) NOT NULL,
    status      varchar(20) NOT NULL,
    payload     jsonb NOT NULL,
    dedup_key   text NOT NULL,
    created_at  timestamptz NOT NULL,
    expires_at  timestamptz,
    decided_at  timestamptz
  );
  CREATE UNIQUE INDEX ux_suggestion_open_dedup
    ON suggestion (user_id, dedup_key) WHERE status = 'PENDING';   -- one open ask per topic
  CREATE INDEX ix_suggestion_user_status ON suggestion (user_id, status);
  ```
  Note: `status`/`type` are plain varchar (no CHECK constraint), consistent with the project/category
  enum columns — adding enum values stays code-only.

## C5 — Producer (transaction-create, gated by `interventionLevel`)

- **`application/usecases/insight/SuggestionProducer.java`** (or fold into a method called from
  `CreateTransactionUseCase`). On an **EXPENSE** create, after the budget `spent` update:
  1. If `interventionLevel == NOTIFY` → **stop** (pull-only user; no push).
  2. Run the analyzer for the affected category (reuse C1; gives BUDGET_PACE/OVERSPEND_ANOMALY).
  3. Gate: keep only insights `≥ WARN` (GENTLE) or `≥ CRITICAL` (HARD).
  4. For a qualifying budget-overspend insight, build a `RAISE_BUDGET` `Suggestion`:
     `proposedAction = { category, budgetId, resolvedValue = max(trailing3MonthMedian, projectedEndOfMonth) }`,
     `dedupKey = "RAISE_BUDGET:" + category + ":" + period`.
  5. `INSERT` PENDING — rely on the partial-unique index; a conflict means an identical ask is
     already open → skip silently (no duplicate push).
  6. On successful insert, `CreateNotificationUseCase.createNotification(userId, content,
     deepLink)` where `deepLink` encodes the suggestion, e.g. `app://suggestions/{id}` (or a
     `data` payload `{suggestionId, type}` — see `ExpoPushNotificationSender`).
- Keep it **in the same `@Transactional`** as the transaction create, OR fire after commit — decide
  in-session (after-commit avoids a push for a rolled-back transaction; simplest Phase 1 = same tx).

## C6 — Respond endpoint (idempotent) + action execution

- **`POST /api/v1/suggestions/{id}/respond`** body `{ "accept": boolean }` on `SuggestionController`.
- **`RespondToSuggestionUseCase`** (`@Transactional`):
  1. **Idempotent transition** — conditional update, no locks:
     ```sql
     UPDATE suggestion SET status = :newStatus, decided_at = now()
     WHERE id = :id AND user_id = :userId AND status = 'PENDING';
     ```
     0 rows affected → already decided / not owner → no-op (return current state).
  2. If `accept` **and** the update touched a row → execute the action via a **`switch` on type**
     (whitelist only): `RAISE_BUDGET` → read `proposedAction` → `UpdateBudgetUseCase.execute(budgetId,
     userId, new UpdateBudgetRequest{amountLimit = resolvedValue})`. Re-validate sanity, honor the
     consented number.
  3. Status flip **and** mutation in the **same `@Transactional`**.
- **`GET /api/v1/suggestions?status=PENDING`** — list open asks for the in-app card screen.

---

## Build order (dependencies)

```
C1 (analyzer + Insight, pure, fully tested)
 └─ C2 (GenerateInsightsUseCase + GET /insights)            ← satisfies NOTIFY users, no push needed
     └─ C4 (Suggestion domain + table V21 + repo)
         └─ C6 (respond endpoint + RAISE_BUDGET execution)  ← prove the decision round trip
             └─ C5 (producer on tx-create, gated by interventionLevel + push)  ← closes the loop
C3 = confirm the C2 DTO contract is FE-ready (no extra backend)
```
Rationale: C1→C2 delivers value alone (pull insights). C4→C6 proves the persisted decision +
action before wiring the proactive push in C5 (so you can test respond via direct API calls first).

## Test plan

- **Unit (pure):** `SpendingInsightAnalyzerTest` (C1.5) — the defensible core.
- **Unit (use cases, mocked repos):** `GenerateInsightsUseCase` (focus filtering), `RespondToSuggestionUseCase`
  (idempotency: double-accept executes once; dismiss; not-owner no-op; accept→UpdateBudget called),
  `SuggestionProducer` (NOTIFY→no suggestion; GENTLE+WARN→insert+push; dedup conflict→skip).
- **Persistence:** dedup partial-unique index actually blocks a second PENDING insert for the same key.
- Mirror the existing mock-based style (`AddProjectContributionUseCaseTest`); the project's
  full use-case tests are largely `@Disabled` — write **active** tests.

## Risks & fatigue control (where the real effort goes)

- **Notification fatigue is the #1 risk.** Phase-1 controls: (a) dedup partial-unique index — one
  open ask per `(category, period)`; (b) severity gate from `interventionLevel`; (c) `NOTIFY`
  users get **zero** pushes. Cooldown / daily cap deferred to Phase 3 (dedup covers duplicates first).
- LLM is **not** on the critical path (Phase 1 returns structured insights + i18n `code`).
- Push delivery is best-effort (existing `CreateNotificationUseCase` swallows enqueue failures);
  a failed push still surfaces in-app via the PENDING list.

## Open decisions for the implementing session

1. **`payload` storage:** real `jsonb` + Hibernate JSON mapping vs a plain `text` column holding
   JSON. (Recommend `jsonb`; confirm it validates under `ddl-auto: validate`.)
2. **Producer timing:** same `@Transactional` as tx-create vs after-commit hook. (Recommend
   same-tx for Phase 1 simplicity; revisit if a rolled-back tx must not push.)
3. **Trailing window N** for z-score (recommend 6) and **min sample** (recommend 3).
4. **Deep-link scheme** the mobile app expects (`app://suggestions/{id}` vs Expo `data` object) —
   confirm with the front-end before C5.

## Explicitly deferred to Phase 2–3 (do NOT build now)

Dismissal-suppression learning loop (store `dismiss_count` first), more action types
(`CREATE_BUDGET`, `CONTRIBUTE_TO_PROJECT`, …), recurring-cost detector → Pivot A `disposable`,
scheduled digest producer, cooldown/daily cap, exponential-smoothing forecast, and the LLM
narration layer over structured insights.
