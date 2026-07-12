# Redesign — Workstream C Implementation Record

> What was actually built for Workstream C (insight + yes/no suggestion round trip), the
> thesis core. Implemented 2026-06-29 in build order **C1 → C2 → C4 → C6 → C5**.
> Companion to the design doc `redesign-workstream-c-plan.md` (the *why*/*how*); this file is
> the *what-shipped* record, including where the implementation deviated from the plan and why.
>
> **Status:** Workstream C ✅ complete. Phase 1 (B ✅ D ✅ A ✅ C ✅) is now **done**.
> 22 new tests; full suite **209 run / 0 failures / 0 errors** (1 pre-existing DB-context test skipped).

## 0. Guiding principle (honored)

Facts are computed deterministically; the LLM is **not** on the Phase-1 critical path. Insights
carry a stable `code` for front-end i18n instead of prose. If the LLM/Gemini is down, raw
structured insights still return.

```
transactions / budgets  →  SpendingInsightAnalyzer (pure)  →  Insight[]  →  InsightResponse (code + metrics)
                                                            ↘  (gated) Suggestion (persisted) → push notification
```

---

## C1 — `SpendingInsightAnalyzer` (pure domain service)

**Files**
- `domain/services/SpendingInsightAnalyzer.java` — pure, no Spring, no I/O.
- `domain/models/insight/Insight.java` — immutable value object (Lombok `@Builder`, unmodifiable `metrics`).
- `domain/models/insight/InsightType.java` — `OVERSPEND_ANOMALY`, `BUDGET_PACE`.
- `domain/models/insight/Severity.java` — `INFO < WARN < CRITICAL`, with `atLeast(threshold)`.
- `config/DomainServiceConfig.java` — `@Bean` exposing the analyzer (keeps the class Spring-free).
- Test: `domain/services/SpendingInsightAnalyzerTest.java` (13 tests).

**Signature**
```java
List<Insight> analyze(List<Transaction> history, List<Budget> budgets, LocalDate asOf)
BigDecimal trailing3MonthMedian(List<Transaction> history, Category category, YearMonth period)
```

**Method A — overspend anomaly (rolling z-score)**
- Per expense `Category`, build monthly totals; take the up-to-`TRAILING_WINDOW` (=6) months
  *strictly before* `asOf`'s month that actually have data.
- Require `n >= MIN_SAMPLE` (=3) and `stddev != 0`, else **skip** (cold start / no variance → no
  divide-by-zero).
- `z = (currentMonthActual − mean) / populationStddev`; `z ≥ 3` CRITICAL, `z ≥ 2` WARN, else INFO.
- `metrics = {baseline, stddev, actual, zScore, sampleMonths}` (rounded).

**Method B — budget pace projection (linear)**
- `projected = spent / max(1, dayOfMonth) × daysInMonth` (day-1 safe).
- Emits only when `projected > limit`: `ratio ≥ 1.25` CRITICAL else WARN.
- `metrics = {spent, limit, dayOfMonth, daysInMonth, projectedEndOfMonth, overshootRatio}`.

**Method C — trailing-3-month median** — robust to one-off spikes; even count → average of the
two middle values; no data → `ZERO`. Used by C5 to resolve the RAISE_BUDGET number.

**Deviation from plan:** the analyzer takes **no `UserTraitsView` argument**. Trait-based
ordering belongs to the use case (C2) and trait-based gating to the producer (C5); the rules
here are trait-independent, so omitting it keeps the pure core minimal. `asOf` is passed in
(not read from the clock) so every rule is deterministic in tests.

---

## C2 — `GenerateInsightsUseCase` + pull endpoint

**Files**
- `application/usecases/insight/GenerateInsightsUseCase.java` (`@Transactional(readOnly = true)`).
- `application/dtos/responses/insight/InsightResponse.java` (`type, category, severity, period, code, metrics`).
- `presentation/controllers/InsightController.java` — `GET /api/v1/insights`.
- Test: `application/usecases/insight/GenerateInsightsUseCaseTest.java` (2 tests).

**Flow:** loads the trailing window via `TransactionRepository.findByUserIdWithFilters`
(start of `period − 6 months` → `asOf`), current budgets via `BudgetRepository.findByUserIdMonthYear`,
then `analyzer.analyze(...)`. Orders results by the user's `focusMode`:
- `REDUCE_SPENDING` → `OVERSPEND_ANOMALY` ranked ahead of `BUDGET_PACE`, then severity desc.
- otherwise → severity desc, deterministic type/category tie-break (no dropping in Phase 1).

`GET /api/v1/insights` (current month, pull-only) **fully satisfies `NOTIFY` users** — they
never receive a push. Contract is FE-consumable (severity + `code` for i18n + `metrics` map) =
the C3 checklist item.

---

## C4 — `Suggestion` domain + persistence + migration

**Files**
- `domain/models/suggestion/Suggestion.java` — `create(...)`, `accept()/dismiss()/expire()`
  (PENDING-only guards, `Instant` timestamps), `isPending()`.
- `domain/models/suggestion/SuggestionType.java` — `RAISE_BUDGET`.
- `domain/models/suggestion/SuggestionStatus.java` — `PENDING → ACCEPTED | DISMISSED | EXPIRED`.
- `infrastructure/persistence/entities/SuggestionEntity.java` (`@Table("suggestion")`).
- `infrastructure/persistence/mappers/SuggestionMapper.java` (MapStruct `AfterMapping`, `payloadJson ↔ payload`).
- `infrastructure/persistence/repositories/JpaSuggestionRepository.java` + `SuggestionRepositoryImpl.java`.
- `domain/repositories/SuggestionRepository.java`.
- `src/main/resources/db/migration/V21__add_suggestion.sql`.

**Decision — payload storage (open decision #1): `text`, not `jsonb`.** The JPA attribute is a
`String`; `text` avoids any `jsonb`/varchar type clash under `ddl-auto: validate`. The
partial-unique dedup index works identically on `text`.

**Migration V21**
```sql
CREATE TABLE suggestion ( id uuid PK, user_id uuid, type varchar(40), status varchar(20),
  payload text, dedup_key text, created_at timestamptz, expires_at timestamptz, decided_at timestamptz );
CREATE UNIQUE INDEX ux_suggestion_open_dedup ON suggestion (user_id, dedup_key) WHERE status = 'PENDING';
CREATE INDEX ix_suggestion_user_status ON suggestion (user_id, status);
```
`type`/`status` are plain varchar (no CHECK), so adding enum values stays code-only.

**Repository contract**
- `findOpenByUserIdAndDedupKey` — producer pre-check (one open ask per topic).
- `findByUserIdAndStatus` — backs the list endpoint.
- `markDecidedIfPending(id, userId, newStatus, decidedAt)` — `@Modifying` conditional UPDATE,
  returns rows affected (the idempotency primitive).

---

## C6 — Respond endpoint (idempotent) + action execution

**Files**
- `application/usecases/suggestion/RespondToSuggestionUseCase.java` (`@Transactional`).
- `application/usecases/suggestion/ListSuggestionsUseCase.java` (`@Transactional(readOnly = true)`).
- `application/usecases/suggestion/SuggestionPayload.java` — `{ insightSnapshot, proposedAction:{ category, budgetId, resolvedValue } }`.
- `application/usecases/suggestion/SuggestionResponseAssembler.java` — parses payload once via injected `ObjectMapper`.
- `application/dtos/requests/suggestion/RespondToSuggestionRequest.java` (`{ accept }`).
- `application/dtos/responses/suggestion/SuggestionResponse.java`.
- `presentation/controllers/SuggestionController.java` — `POST /api/v1/suggestions/{id}/respond`, `GET /api/v1/suggestions?status=`.
- Test: `application/usecases/suggestion/RespondToSuggestionUseCaseTest.java` (4 tests).

**Respond flow** (one `@Transactional`):
1. Load + **owner check** (non-owner → `403`-mapped `DomainException`; not the plan's silent no-op —
   returning another user's state would leak data).
2. `markDecidedIfPending(...)` conditional UPDATE. **0 rows → idempotent no-op** (already decided /
   lost race) → return current state.
3. On `accept` **and** a touched row → `switch` on type (whitelist): `RAISE_BUDGET` → read
   `proposedAction` → `UpdateBudgetUseCase.execute(budgetId, userId, {amountLimit = resolvedValue})`.
   Status flip + budget mutation are atomic.

---

## C5 — Producer (transaction-create, gated by `interventionLevel`)

**Files**
- `application/usecases/insight/SuggestionProducer.java` (`@Service`, best-effort, **never throws**).
- Wired into `application/usecases/transaction/CreateTransactionUseCase.java` — called on **EXPENSE**
  create, after the budget `spent` update, with `transactionDate.toLocalDate()` as `asOf`.
- Test: `application/usecases/insight/SuggestionProducerTest.java` (3 tests).

**Flow**
1. `interventionLevel` null/`NOTIFY` → **stop** (pull-only).
2. Push gate: `GENTLE → WARN`, `HARD → CRITICAL`.
3. Run analyzer; keep the worst `BUDGET_PACE` insight for the affected category that clears the gate
   (only a budget-tied insight maps to RAISE_BUDGET).
4. `resolvedValue = max(trailing3MonthMedian, projectedEndOfMonth)`;
   `dedupKey = "RAISE_BUDGET:" + category + ":" + period`.
5. Pre-check `findOpenByUserIdAndDedupKey` → skip if open; the partial-unique index is the
   concurrent-race backstop (insert conflict caught → skip).
6. Push via `CreateNotificationUseCase.createNotification(userId, content, "app://suggestions/{id}")`.

**Decision — producer timing (open decision #2): same call from `CreateTransactionUseCase`, not an
after-commit hook.** `CreateTransactionUseCase` has **no** surrounding `@Transactional`, so each
repo `save` already commits independently; the producer's saves run in their own transactions,
which sidesteps the Postgres "aborted transaction on constraint conflict" trap. The whole producer
is wrapped in try/catch so a failure never affects the user's transaction create.

---

## Decisions log (the plan's "open decisions")

| # | Decision | Choice made | Rationale |
|---|---|---|---|
| 1 | payload storage | **`text`** holding JSON | passes `ddl-auto: validate` with a `String` attribute |
| 2 | producer timing | **same-flow call**, own-tx saves | avoids aborted-tx-on-conflict; never blocks tx create |
| 3 | window N / min sample | **N=6, min=3** | plan recommendation |
| 4 | deep-link scheme | **`app://suggestions/{id}`** | plan recommendation — ⚠️ **still unconfirmed with FE** |

## What is intentionally NOT built yet (deferred — see `redesign-phase2-3-plan.md`)

LLM narration layer; dismissal-suppression learning; more action types (`CREATE_BUDGET`,
`CONTRIBUTE_TO_PROJECT`); recurring-cost detector → Pivot A `disposable`; scheduled digest;
cooldown / daily cap; exponential-smoothing forecast; `CATEGORY_TREND`/`RECURRING`/`SAVINGS_CAPACITY`
insight types; `savingPace`-scaled savings amounts; D2 trait-onboarding endpoint (skipped in Phase 1).

## Verification status / known gaps

- ✅ Compiles; 22 new unit tests green; full suite 209/0/0 (1 skipped).
- ⚠️ **Spring context boot not exercised** — `SmartmoneyApplicationTests` is skipped (needs a real DB),
  so the new beans + `V21` + `ddl-auto: validate` were verified by compilation + unit tests, not a
  live boot. **Next step before merge:** run the app against a dev DB once to confirm `V21` applies
  and `SuggestionEntity` validates.
- ⚠️ Deep-link scheme (`app://suggestions/{id}`) and the notification content key
  `notification.suggestion.raise_budget|<CATEGORY>` need FE confirmation / i18n entries.
