# Adaptive Engine — Mobile FE Handoff

**Audience:** the FE agent building the insights + suggestions experience.
**Scope:** the redesign's proactive loop — pulled **insights**, pushed yes/no **suggestions**, the
trait **onboarding**, and the **notification deep-link** that ties them together.

**Executable companion:** [`docs/api-testing/`](../api-testing/README.md) — a Postman/Bruno
collection that runs this exact flow end-to-end. Every request/response below is drawn from it.

---

## Mental model the UI must teach

> The app watches the user's own transactions and, at most, **asks one small yes/no question** when
> it spots something worth acting on. It never moves money on its own. Two surfaces:
>
> - **Insights (pull):** a read-only "what's happening" list the user opens any time. This alone
>   fully serves users who don't want to be interrupted.
> - **Suggestions (push):** when the user has opted into nudges, a high-value insight becomes a
>   **notification** → tapping it opens an **in-app card** with one action → the user taps **Yes**
>   (the app performs the action) or **No** (dismiss).
>
> **The backend emits machine-readable enums + numbers; the FE composes all display copy and i18n.**
> Never show a raw `code` — localize it. Never recompute a number — render the ones given.

### The three fatigue promises (so the UI matches behaviour)

The user will *not* be spammed, because the backend already enforces: one open ask per topic;
back-off on topics they keep dismissing; a per-topic cooldown; and a daily push cap. The FE doesn't
implement any of this — just don't build UI that assumes a suggestion always appears after an event.

---

## Global conventions

- **Base URL:** `http://localhost:8080` (dev). All paths below are under `/api/v1`.
- **Auth:** `Authorization: Bearer <accessToken>` on every call except `/auth/login|register|...`.
  Get the token from `POST /api/v1/auth/login` → `data.accessToken` (+ `data.refreshToken`).
- **Response envelope:** every endpoint returns
  ```json
  { "success": true, "message": "…", "data": <payload> }
  ```
  Check `success`; show `message` on failure. `data` is the useful part. Fields that are null are
  omitted (`@JsonInclude(NON_NULL)`), so code defensively.
- **Money:** integer-ish `BigDecimal` (app currency is VND-scale in the fixtures). Render with the
  user's locale; don't assume 2 decimals matter.
- **Deep-link scheme:** `app://suggestions/{suggestionId}` — carried on notifications (see Screen 4).

---

## Screen 1 — Trait onboarding ("How should we help?")

Cold-start setup. Until the user picks traits, they get the least-spammy defaults (pull-only, no
pushes). This screen is what turns the proactive engine on.

### Read current setup
`GET /api/v1/auth/me/financial-setup`
```json
{ "success": true, "data": {
  "income": 20000000,
  "savingPace": "BALANCED",
  "interventionLevel": "GENTLE",
  "focusMode": "REDUCE_SPENDING",
  "financialSetupCompleted": true
}}
```
`financialSetupCompleted=false` (or null traits) → show onboarding. Any of the trait fields may be
`null` before setup — treat null as "not chosen yet".

### Save setup
`PUT /api/v1/auth/me/financial-setup`
```json
{ "income": 20000000, "savingPace": "BALANCED", "interventionLevel": "GENTLE", "focusMode": "REDUCE_SPENDING" }
```
All four fields are **required** and validated (`income > 0`). Returns the same shape as the read.
There is also `DELETE /me/financial-setup` to clear it.

### What each trait actually does (drives your copy)

| Trait | Values | Effect the user feels |
|---|---|---|
| `interventionLevel` | `NOTIFY` · `GENTLE` · `HARD` | **How often we push.** `NOTIFY` = never push (insights only). `GENTLE` = push warnings + emergencies. `HARD` = push emergencies only. |
| `savingPace` | `RELAXED` · `BALANCED` · `AGGRESIVE` *(spelling per API)* | **How big a save we suggest** — 10% / 20% / 30% of spare money. |
| `focusMode` | `REDUCE_SPENDING` · `SAVE_MORE` · `TRACK_ONLY` | **What we surface first** in the insights list (spending problems vs. saving chances vs. no preference). |

> Copy suggestion: frame `interventionLevel` as "How much should we nudge you?" with NOTIFY = "Just
> let me check in myself", GENTLE = "Warn me when it matters", HARD = "Only for emergencies".

---

## Screen 2 — Insights list (pull)

The always-available "what's happening this month" feed. Read-only; safe to poll on screen focus.

`GET /api/v1/insights` → `data` is an **ordered** array (most relevant first, already sorted by the
user's `focusMode` then severity — render in the order given).

> **Optional `?asOf=YYYY-MM-DD`** analyzes the month of that date instead of today. Production apps
> won't need it — it exists for **testing/demos** so seeded past-month data can be viewed
> deterministically (e.g. `?asOf=2026-05-15`). Omit it for the normal "current month" view.

```json
{ "success": true, "data": [
  {
    "type": "BUDGET_PACE",
    "category": "FOOD",
    "severity": "WARN",
    "period": "2026-07",
    "code": "insight.budget_pace.food",
    "metrics": { "spent": 2000000.00, "limit": 10000000.00, "dayOfMonth": 5,
                 "daysInMonth": 31, "projectedEndOfMonth": 12400000.00,
                 "overshootRatio": 1.24, "forecastMethod": "LINEAR" },
    "narrative": null
  }
]}
```

Render rules:
- **`code`** → your i18n key. Interpolate values from `metrics`. `narrative` is an **optional**
  server-composed sentence (LLM); when non-null you *may* show it instead of your localized string,
  but always keep `code` as the fallback (narration can be off/unavailable → `narrative: null`).
- **`severity`** → visual weight: `INFO` (neutral) · `WARN` (amber) · `CRITICAL` (red).
- **`category`** may be `null` (e.g. `SAVINGS_CAPACITY` is account-wide).
- **`period`** is ISO year-month (`"2026-07"`).

### Insight types and their `metrics` (what you can render)

| `type` | Meaning | Key `metrics` |
|---|---|---|
| `OVERSPEND_ANOMALY` | This month's spend in a category is abnormally high vs. its own history | `baseline`, `stddev`, `actual`, `zScore`, `sampleMonths` |
| `BUDGET_PACE` | On current pace, a budget will be blown by month-end | `spent`, `limit`, `dayOfMonth`, `daysInMonth`, `projectedEndOfMonth`, `overshootRatio`, `forecastMethod` (+`smoothedDailyRate`) |
| `CATEGORY_TREND` | A category is steadily creeping up over recent months | `slope`, `meanMonthly`, `growthRatePerMonth`, `sampleMonths` |
| `RECURRING` | A stable repeating charge was detected (feeds savings math) | `monthlyAmount`, `monthsObserved`, `description` |
| `SAVINGS_CAPACITY` | There's spare money to save this month | `avgMonthlyIncome`, `recurringCosts`, `committedBudgets`, `disposable` |

`metrics` is an open map — treat unknown keys as ignorable, so new metrics never break your render.

---

## Screen 3 — Suggestion card (the yes/no ask)

A suggestion is a persisted, decidable question. The user reaches it two ways: from a **notification
tap** (Screen 4) or from a **suggestions inbox** you can build on the list endpoint.

### List
`GET /api/v1/suggestions?status=PENDING` (`status` optional, defaults to `PENDING`; also
`ACCEPTED` · `DISMISSED` · `EXPIRED`).

```json
{ "success": true, "data": [
  {
    "id": "0b0e…-uuid",
    "type": "RAISE_BUDGET",
    "status": "PENDING",
    "dedupKey": "RAISE_BUDGET:FOOD:2026-07",
    "payload": {
      "insightSnapshot": { "type": "BUDGET_PACE", "severity": "WARN",
                           "code": "insight.budget_pace.food", "period": "2026-07",
                           "metrics": { "projectedEndOfMonth": 12400000.00, "limit": 10000000.00 } },
      "proposedAction": { "category": "FOOD", "budgetId": "…-uuid", "resolvedValue": 12400000.00 }
    },
    "createdAt": "2026-07-05T03:00:00Z",
    "expiresAt": null,
    "decidedAt": null
  }
]}
```

### Fetch one (for the deep-link route)
There is no single-suggestion GET; fetch `?status=PENDING` and select by `id`, or list and cache.
(If you want a dedicated `GET /suggestions/{id}`, ask backend — it's a small add.)

### Respond (Yes / No)
`POST /api/v1/suggestions/{id}/respond`
```json
{ "accept": true }
```
- `accept:true` → backend **executes the proposed action** and returns the suggestion with
  `status:"ACCEPTED"`.
- `accept:false` → `status:"DISMISSED"` (and quietly teaches the engine to back off this topic).
- **Idempotent:** responding again returns the current state and does **not** re-run the action.
  Safe to retry on flaky network. A missing/foreign id returns 404/403 in `message`.

### What the card renders per `type`

Build the ask sentence from `type` + `proposedAction`. **Show `resolvedValue` as the exact number
the user is consenting to** — never recompute it.

| `type` | The ask | `proposedAction` fields | On **Yes**, backend does |
|---|---|---|---|
| `RAISE_BUDGET` | "Raise your {category} budget to {resolvedValue}?" | `category`, `budgetId`, `resolvedValue` | updates that budget's limit |
| `CREATE_BUDGET` | "Set a {category} budget of {resolvedValue} for this month?" | `category`, `month`, `year`, `resolvedValue` | creates the budget |
| `CONTRIBUTE_TO_PROJECT` | "Put {resolvedValue} toward {project}?" | `projectId`, `resolvedValue` | records a contribution (an EXPENSE ledger entry) |

`insightSnapshot` is the frozen reason ("why are you asking?") — use it to show the supporting
number(s) on the card. It's a snapshot, so it stays stable even if live data later changes.

### Suggestion state machine

```
PENDING ──accept──▶ ACCEPTED   (action executed once)
   │
   ├────dismiss──▶ DISMISSED
   │
   └────(time)───▶ EXPIRED      (never acted on)
```
Only `PENDING` is actionable. For `ACCEPTED`/`DISMISSED`/`EXPIRED`, render a read-only past-tense
state, no buttons.

---

## Screen 4 — Notification inbox & deep-link routing

Pushes and the in-app inbox share one model. A suggestion push is just a notification whose
`deepLink` points at the suggestion.

`GET /api/v1/notifications` → newest first:
```json
{ "success": true, "data": [
  { "id": "…", "userId": "…",
    "content": "notification.suggestion.raise_budget|FOOD",
    "deepLink": "app://suggestions/0b0e…-uuid",
    "createdAt": "2026-07-05T10:00:00", "read": false }
]}
```

Routing contract:
- **`content` is an i18n key**, optionally `key|arg` (split on `|` — the arg is a CATEGORY or a
  project name). Localize it; never display the raw key. *(If server-side narration is enabled, this
  may already be a natural sentence — render as-is when it isn't a `notification.*` key.)*
- **`deepLink`** — routing by shape:
  - `app://suggestions/{id}` → open the **Suggestion card** (Screen 3) for that `{id}`.
  - `app://suggestions` (no id) → open the **suggestions list/inbox** (Screen 3's list). This is the
    **daily roll-up** ping (`content = notification.digest.pending_suggestions`): when the user hits
    the daily push cap, extra suggestions are still created as cards but their individual pings are
    replaced by this one "you have more to review" notification. So the list may contain PENDING
    cards the user was never individually pinged about — that's expected, not a bug.
  - `null` (plain receipts like `notification.expense`) → route to their normal place / no-op.
- **Mark read:** `PATCH /api/v1/notifications/read` with `{ "notificationIds": ["…","…"] }`.

> This matches the agreed UX: the push has **no in-banner Yes/No buttons** — tapping it always lands
> on the in-app card, which is the single place a decision is made.

---

## Appendix A — Enum reference (exact API spellings)

| Enum | Values |
|---|---|
| `Intervention_Level` | `NOTIFY`, `GENTLE`, `HARD` |
| `Saving_Pace` | `RELAXED`, `BALANCED`, `AGGRESIVE` *(sic — one S)* |
| `Focus_Mode` | `SAVE_MORE`, `REDUCE_SPENDING`, `TRACK_ONLY` |
| `Severity` | `INFO`, `WARN`, `CRITICAL` |
| `InsightType` | `OVERSPEND_ANOMALY`, `BUDGET_PACE`, `CATEGORY_TREND`, `RECURRING`, `SAVINGS_CAPACITY` |
| `SuggestionType` | `RAISE_BUDGET`, `CREATE_BUDGET`, `CONTRIBUTE_TO_PROJECT` |
| `SuggestionStatus` | `PENDING`, `ACCEPTED`, `DISMISSED`, `EXPIRED` |
| `Category` (transactions) | `FOOD`, `TRANSPORTATION`, `CLOTHING`, `UTILITIES`, `ENTERTAINMENT`, `HEALTH`, `EDUCATION`, `SHOPPING`, `OTHER`, … |
| `TransactionType` | `EXPENSE`, `INCOME` |

## Appendix B — i18n keys the FE must provide

Insight `code`s (category lower-cased):
```
insight.overspend_anomaly.<category>
insight.budget_pace.<category>
insight.category_trend.<category>
insight.recurring.<category>
insight.savings_capacity
```
Notification `content` keys (may carry `|<ARG>`):
```
notification.suggestion.raise_budget|<CATEGORY>
notification.suggestion.create_budget|<CATEGORY>
notification.suggestion.contribute_to_project|<PROJECT_NAME>
notification.digest.pending_suggestions      # daily roll-up → deep-links to app://suggestions
notification.expense        notification.income
```
Provide one localized template per key; interpolate numbers from the insight `metrics` /
suggestion `proposedAction`.

## Appendix C — Screen build checklist

- [ ] **Onboarding** — read/save `financial-setup`; gate the proactive UI on `interventionLevel`.
- [ ] **Insights list** — `GET /insights`; localize `code` + `metrics`; render in returned order;
      severity styling; handle `category:null` and `narrative:null`.
- [ ] **Suggestion card** — per-`type` ask from `proposedAction`; Yes/No → `respond`; idempotent;
      read-only for non-PENDING.
- [ ] **Suggestions inbox** *(optional)* — `GET /suggestions?status=…` with tabs.
- [ ] **Notifications** — inbox list; `deepLink` routing to the card; mark-read; localize `content`.
- [ ] **Empty/gated states** — NOTIFY user sees insights but never a push; no history yet → empty
      insights (this is normal, not an error).

## Appendix D — Open items to confirm with backend

1. No `GET /suggestions/{id}` today — deep-link handler must fetch the list and filter. Add a
   by-id endpoint if the FE prefers.
2. Per-user **locale** for `narrative` isn't plumbed yet (defaults to English). i18n via `code` is
   the reliable path.
3. Budget list / project detail DTO field names weren't pinned in this doc — confirm against
   `BudgetListResponse` / `ProjectDetailResponse` when wiring the "verify it changed" screens.
