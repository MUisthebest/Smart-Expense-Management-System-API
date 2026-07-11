# Adaptive Engine — Mobile FE Handoff

**Audience:** the FE agent building the insights + suggestions experience.
**Scope:** the redesign's proactive loop — pulled **insights**, pushed yes/no **suggestions**, the
trait **onboarding**, and the **notification deep-link** that ties them together.

**Executable companion:** [`docs/api-testing/`](../api-testing/README.md) — a Postman/Bruno
collection that runs this exact flow end-to-end. Every request/response below is drawn from it.

**Sibling artifacts in this folder:**
- [`i18n.en.reference.json`](i18n.en.reference.json) — starter English copy for every key in
  Appendix B, placeholders named after the real metric/payload fields.
- `openapi.json` — the full API spec (`/v3/api-docs` export) for everything outside this guide's
  scope (transactions, budgets, projects, auth). Regenerate: `scripts/capture-fe-handoff.ps1`.
- `fixtures/` — real captured responses from a seeded run; use as mock data. Same script.
- Product rationale (the "why" behind every rule here): [`../redesign-what-and-why.md`](../redesign-what-and-why.md).
  Ignore the `project-tracking-*` docs — superseded, see [`../README.md`](../README.md).

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

### Settled decisions — don't re-open these

These look like open UX questions but were explicitly decided; build to them as stated:

1. **No Yes/No buttons in the push banner.** Tapping a notification always lands on the in-app
   card; the card is the *only* surface where a decision is made.
2. **The app never moves money or changes budgets on its own.** Every action is gated on an
   explicit Yes. Don't add "auto-accept" conveniences.
3. **The user consents to the exact `resolvedValue` shown.** Never recompute, round for display
   only, and never let an edited number silently replace it (a different amount = the user acts
   manually via the normal budget/project screens, not via the suggestion).
4. **Insights alone must be a complete experience** for `NOTIFY` users — don't design the insights
   screen as a teaser that pushes people toward enabling suggestions.

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
| `BUDGET_OVER_INCOME` | This month's budget limits add up to more than declared income (account-wide, `category:null`) — a **soft**, never-enforced warning to rebalance | `committedBudgets`, `income`, `overBy`, `overshootRatio` |
| `BUDGET_UNDERSPEND` | A budgeted category is consistently used well under its limit — the budget can be trimmed (backs `REDUCE_BUDGET`) | `limit`, `medianActual`, `projectedEndOfMonth`, `underspendRatio` |
| `SUBSCRIPTION_PRICE_HIKE` | A stable recurring charge just posted noticeably above its usual price — the subscription got more expensive (backs `REVIEW_SUBSCRIPTION`) | `description`, `previousAmount`, `newAmount`, `increaseRatio`, `monthsObserved` |

`metrics` is an open map — treat unknown keys as ignorable, so new metrics never break your render.

**Notification-only insights** are **not** returned by `GET /insights` and never become suggestion
cards. There's no accept action; they're informational pings (Screen 4) only. Three groups:

- **Per-transaction** (`LARGE_TRANSACTION`, `DUPLICATE_CHARGE`) — evaluated synchronously when an
  expense is created: a large single charge vs. that category's typical size, or a likely
  double-charge (same category + exact amount + description within 72h).
- **Monthly recap** (`INCOME_DROP`, `CASHFLOW_NEGATIVE`) — evaluated by a scheduler shortly after a
  month **closes** (these facts are meaningless mid-month): last month's income collapsed vs. its
  usual level, or last month spent more than it earned. Both deep-link to `app://transactions` and
  respect the same `interventionLevel` gate as pushes (NOTIFY users never get them). Backend
  feature-flagged (`app.recap.enabled`, off by default), like the weekly digest.
- **Positive reinforcement** (`PROJECT_MILESTONE`, `GOOD_MONTH` — Wave 4) — the engine's only
  *celebratory* pings: a contribution pushed a saving project past 25/50/75/100% (deep link
  `app://projects/{id}`), or a closed month finished with **every** budget under its limit (deep
  link `app://budgets`, rides the recap and only fires when the month had nothing negative). Each
  milestone/month is congratulated **at most once**. Gate is presence-of-push: `GENTLE` *and*
  `HARD` users get them; `NOTIFY` users don't. Style these as good news — no amber/red.

Related but distinct: the **create-project nudge** (`notification.nudge.create_saving_project`,
Wave 4) — when the user has savings capacity but **no active project** to receive it, a push
deep-links to `app://projects/create?amount=<seed>`. Open the project-create form **pre-filled**
with that amount (user edits freely — name/target/deadline are theirs to choose). No suggestion
card, no accept step, at most once per month.

One more insight type exists that appears **only inside a suggestion card's `insightSnapshot`**,
never in `GET /insights`: `PROJECT_OFF_TRACK` (backs `INCREASE_CONTRIBUTION`, Wave 4). Its
`metrics`: `projectName`, `remaining`, `deadline`, `monthsLeft`, `requiredMonthly`,
`observedMonthly`, `shortfall`, `observedWindowMonths`.

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
    "narrative": null,
    "payload": {
      "insightSnapshot": { "type": "BUDGET_PACE", "severity": "WARN",
                           "code": "insight.budget_pace.food", "period": "2026-07",
                           "metrics": { "projectedEndOfMonth": 12400000.00, "limit": 10000000.00 } },
      "proposedAction": { "category": "FOOD", "budgetId": "…-uuid", "resolvedValue": 12400000.00 },
      "narrative": null
    },
    "createdAt": "2026-07-05T03:00:00Z",
    "expiresAt": null,
    "decidedAt": null
  }
]}
```

> **`narrative`** — an **optional** server-composed sentence (LLM), the same one used in the
> suggestion's push body, frozen onto the card at ask time. Exposed both top-level (a peer of
> `type`/`status`, use this) and inside `payload.narrative` (the raw snapshot). When non-null you
> *may* show it as the card's headline instead of composing your own from `type` + `proposedAction`;
> when `null` (narration off/unavailable — the default), compose the ask yourself as below. It is a
> garnish, never a replacement: the `resolvedValue` and the per-`type` action are still the source of
> truth for what the user consents to — see settled decision #3.

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
| `SET_CATEGORY_LIMIT` | "Set a {category} budget of {resolvedValue} for this month?" | `category`, `month`, `year`, `resolvedValue` | creates the budget (first limit for a trending, budgetless category) |
| `REDUCE_BUDGET` | "Lower your {category} budget to {resolvedValue}?" | `category`, `budgetId`, `resolvedValue` | updates that budget's limit (downward) |
| `CONTRIBUTE_TO_PROJECT` | "Put {resolvedValue} toward {project}?" | `projectId`, `resolvedValue` | records a contribution (an EXPENSE ledger entry) |
| `REBALANCE_BUDGETS` | "Your budgets total more than your income — shrink them to fit ({resolvedValue} total)?" | `month`, `year`, `resolvedValue` (new committed total), `budgetAdjustments[]` | updates **every listed budget** to its `newLimit` |
| `REALLOCATE_BUDGET` | "Your {category} budget is about to overflow — cover it from budgets you're not using ({resolvedValue} moved)?" | `category`, `month`, `year`, `resolvedValue` (amount moved), `budgetAdjustments[]` | updates **every listed budget** to its `newLimit` (recipient up, donors down) — same executor as `REBALANCE_BUDGETS` |
| `REVIEW_SUBSCRIPTION` | "{description} went up from {previousAmount} to {newAmount} — reviewed it?" *(numbers from `insightSnapshot.metrics`)* | **none** (`proposedAction` is `null`) | **nothing** — acknowledge-only; Yes just marks it reviewed |
| `INCREASE_CONTRIBUTION` | "{project} is behind — add {resolvedValue} this month to catch up?" *(supporting `requiredMonthly`/`observedMonthly` from `insightSnapshot.metrics`)* | `projectId`, `resolvedValue` | records a contribution (same executor as `CONTRIBUTE_TO_PROJECT`) |

`insightSnapshot` is the frozen reason ("why are you asking?") — use it to show the supporting
number(s) on the card. It's a snapshot, so it stays stable even if live data later changes.

**`REBALANCE_BUDGETS` / `REALLOCATE_BUDGET` card specifics** (the multi-entity asks — both use
`budgetAdjustments[]` and the same all-or-nothing executor):
- `proposedAction.budgetAdjustments` is a list of
  `{ "budgetId", "category", "currentLimit", "newLimit" }` — render a per-budget
  **currentLimit → newLimit** table so the user sees exactly what Yes applies. Consent is to this
  snapshot: the backend applies these numbers verbatim (a budget deleted in the meantime is
  skipped), never recomputes. If a donor has spent past its lowered limit by the time the user taps
  Yes, the **whole ask is rejected** (nothing changes) and an error is returned — re-fetch and let
  the user try again.
- `REBALANCE_BUDGETS` is raised by **creating or editing a budget** (its `dedupKey` is account-wide
  per month, `REBALANCE_BUDGETS:2026-07`). `REALLOCATE_BUDGET` is raised by **logging an expense**
  that puts a budgeted category on pace to overflow: it moves headroom *from* underspent budgets
  *into* the overflowing one, so the committed total is unchanged. Its `dedupKey` is per recipient
  category per month (`REALLOCATE_BUDGET:FOOD:2026-07`), and it's offered **in preference to**
  `RAISE_BUDGET` when other budgets can fully cover the gap (only if they can't does `RAISE_BUDGET`
  fire instead).
- `REVIEW_SUBSCRIPTION` has **no** `proposedAction` — build the whole card from
  `insightSnapshot.metrics` (`description`, `previousAmount`, `newAmount`). Yes = "I've looked at
  it"; the actual cancelling/renegotiating happens outside the app.

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
  - `app://insights` → open the **Insights list** (Screen 2). Carried by the **weekly digest**
    (`content = notification.digest.weekly|<count>`, count = actionable ≥WARN insights) — a Monday
    batch ping for low-interruption users. Backend feature-flagged (`app.digest.enabled`, off by
    default), but wire the route so it works when enabled.
  - `app://transactions` → open the **transactions list** (per-transaction anomaly and monthly
    recap pings).
  - `app://projects/{id}` → open that **project's detail** (milestone celebrations).
  - `app://budgets` → open the **budgets screen** (GOOD_MONTH celebration).
  - `app://projects/create?amount={seed}` → open the **project-create form pre-filled** with the
    seed amount (the create-project nudge; the user edits everything freely).
  - `null` (plain transaction receipts, key `notification.notification_done|…`) → route to their
    normal place / no-op.
- **Mark read:** `PATCH /api/v1/notifications/read` with `{ "notificationIds": ["…","…"] }`.

> This matches the agreed UX: the push has **no in-banner Yes/No buttons** — tapping it always lands
> on the in-app card, which is the single place a decision is made.

---

## Screen 5 — Manual contribute button (press-to-contribute) — ⚠️ NOT BUILT YET

**Status: the FE does not have this button yet — it's the main gap to close.** It's the *primary* way
a user funds a saving goal, and it's what most of the project flow above quietly assumes exists. Until
it ships, a goal can only gain progress two other ways: the user **accepts** a `CONTRIBUTE_TO_PROJECT`
or `INCREASE_CONTRIBUTION` suggestion (Screen 3), or the monthly **settlement engine** auto-saves into
it (server-side, off by default). All three write the **same** thing — a `ProjectContribution` — and
progress is `sum(contributions)`, so they add up into one number.

### The endpoint
`POST /api/v1/projects/{projectId}/contributions`
```json
{ "amount": 1000000, "note": "optional, ≤255 chars" }
```
Returns the updated project (`data`, same `ProjectDetailResponse` as Appendix D): `totalContributed` /
`netSaved` / `progressPercent` move by `amount`. `note` is optional.

### Rules the button must respect
- **`amount > 0`** (validated; `0.01` minimum). Currency is the project's.
- **Only a contributable project accepts it.** A `FROZEN` / `EXPIRED` / `ABANDONED` / `COMPLETED`
  project returns **400** ("project not contributable") — disable/hide the button for those states
  (`status` from Appendix D).
- **Reaching the target auto-completes** the project synchronously (`status → COMPLETED` in the
  response) — reflect that immediately.
- **Crossing a 25/50/75/100 % milestone** fires a one-time celebration **notification**
  (`app://projects/{id}`, Screen 4) — no extra call needed; it just appears in the inbox.
- Writes a real `EXPENSE` ledger row (`Category.PROJECT_CONTRIBUTION`) carrying the project id, so it
  also shows in the transactions list — localize that category (Appendix A).

### Why it's the same as accepting a contribute suggestion
Accepting `CONTRIBUTE_TO_PROJECT` / `INCREASE_CONTRIBUTION` (Screen 3) calls this exact use case
server-side with the consented `resolvedValue`. So the suggestion flow already works **without** the
button — but the button is the everyday, unprompted path, and the milestone/completion behaviour above
applies identically whether the contribution came from a press, a suggestion accept, or settlement.

> Build it as: an **amount field + "Contribute" button** on the project detail screen, gated on
> `status` being active/ongoing. On success, re-render progress from the returned `data`.

---

## Appendix A — Enum reference (exact API spellings)

| Enum | Values |
|---|---|
| `Intervention_Level` | `NOTIFY`, `GENTLE`, `HARD` |
| `Saving_Pace` | `RELAXED`, `BALANCED`, `AGGRESIVE` *(sic — one S)* |
| `Focus_Mode` | `SAVE_MORE`, `REDUCE_SPENDING`, `TRACK_ONLY` |
| `Severity` | `INFO`, `WARN`, `CRITICAL` |
| `InsightType` | `OVERSPEND_ANOMALY`, `BUDGET_PACE`, `CATEGORY_TREND`, `RECURRING`, `SAVINGS_CAPACITY`, `BUDGET_OVER_INCOME`, `BUDGET_UNDERSPEND`, `SUBSCRIPTION_PRICE_HIKE` (+ never in `GET /insights`: notification-only `LARGE_TRANSACTION`, `DUPLICATE_CHARGE`, `INCOME_DROP`, `CASHFLOW_NEGATIVE`, `PROJECT_MILESTONE`, `GOOD_MONTH`; card-snapshot-only `PROJECT_OFF_TRACK`) |
| `SuggestionType` | `RAISE_BUDGET`, `CREATE_BUDGET`, `SET_CATEGORY_LIMIT`, `REDUCE_BUDGET`, `CONTRIBUTE_TO_PROJECT`, `REBALANCE_BUDGETS`, `REALLOCATE_BUDGET`, `REVIEW_SUBSCRIPTION`, `INCREASE_CONTRIBUTION` |
| `SuggestionStatus` | `PENDING`, `ACCEPTED`, `DISMISSED`, `EXPIRED` |
| `Category` (transactions) | **Expense:** `FOOD`, `TRANSPORTATION`, `CLOTHING`, `UTILITIES`, `ENTERTAINMENT`, `HEALTH`, `EDUCATION`, `SHOPPING`, `OTHER`. **Income:** `SALARY`, `BONUS`, `INVESTMENT`, `GIFT` (pair with `type: INCOME`). **System-generated ledger entries:** `PROJECT_REFUND`, `PROJECT_CONTRIBUTION` (accepting `CONTRIBUTE_TO_PROJECT` writes a `PROJECT_CONTRIBUTION` expense; abandoning a project writes `PROJECT_REFUND` income), so localize them in transaction lists. Category is not restricted per type at the API — any value pairs with either `type`. |
| `TransactionType` | `EXPENSE`, `INCOME` |

## Appendix B — i18n keys the FE must provide

Insight `code`s (category lower-cased):
```
insight.overspend_anomaly.<category>
insight.budget_pace.<category>
insight.category_trend.<category>
insight.recurring.<category>
insight.savings_capacity
insight.budget_over_income
insight.budget_underspend.<category>
insight.subscription_price_hike.<category>
insight.project_off_track          # card-snapshot only (INCREASE_CONTRIBUTION), no category
```
Notification `content` keys (may carry `|<ARG>`):
```
notification.suggestion.raise_budget|<CATEGORY>
notification.suggestion.create_budget|<CATEGORY>
notification.suggestion.set_category_limit|<CATEGORY>
notification.suggestion.reduce_budget|<CATEGORY>
notification.suggestion.contribute_to_project|<PROJECT_NAME>
notification.suggestion.rebalance_budgets    # account-wide, no arg
notification.suggestion.review_subscription|<DESCRIPTION>   # arg = the subscription's description
notification.suggestion.increase_contribution|<PROJECT_NAME>  # catch-up ask → app://suggestions/{id}
notification.insight.large_transaction|<CATEGORY>   # notification-only ping → app://transactions
notification.insight.duplicate_charge|<CATEGORY>    # notification-only ping → app://transactions
notification.insight.income_drop            # monthly recap ping (no arg) → app://transactions
notification.insight.cashflow_negative      # monthly recap ping (no arg) → app://transactions
notification.insight.project_milestone|<PROJECT_NAME>  # positive ping → app://projects/{id}
notification.insight.good_month             # positive ping (no arg) → app://budgets
notification.nudge.create_saving_project    # deep-link nudge → app://projects/create?amount=<seed>
notification.digest.pending_suggestions      # daily roll-up → deep-links to app://suggestions
notification.digest.weekly|<COUNT>           # weekly digest (opt-in flag) → deep-links to app://insights
notification.notification_done|<notification.expense OR notification.income>|<AMOUNT>|<CATEGORY>
                                             # transaction receipt — 3 pipe args, no deepLink

```
Provide one localized template per key; interpolate numbers from the insight `metrics` /
suggestion `proposedAction`.

## Appendix C — Screen build checklist

- [ ] **Onboarding** — read/save `financial-setup`; gate the proactive UI on `interventionLevel`.
- [ ] **Insights list** — `GET /insights`; localize `code` + `metrics`; render in returned order;
      severity styling; handle `category:null` and `narrative:null`.
- [ ] **Suggestion card** — per-`type` ask from `proposedAction`; prefer `narrative` as the headline
      when non-null, else compose from `type`; Yes/No → `respond`; idempotent; read-only for non-PENDING.
- [ ] **Suggestions inbox** *(optional)* — `GET /suggestions?status=…` with tabs.
- [ ] **Notifications** — inbox list; `deepLink` routing to the card; mark-read; localize `content`.
- [ ] **Manual contribute button** *(Screen 5 — currently missing)* — amount field + "Contribute" on
      the project detail; `POST /projects/{id}/contributions`; gate on active status; re-render from
      the returned project. The primary goal-funding action the suggestion accepts already reuse.
- [ ] **Empty/gated states** — NOTIFY user sees insights but never a push; no history yet → empty
      insights (this is normal, not an error).

## Appendix D — "Verify it changed" DTOs + open items

After a **Yes**, these are the reads that show the effect (pinned from the actual DTOs; the full
spec is in `openapi.json`):

**`GET /api/v1/budgets?month={m}&year={y}`** → `data`:
```json
{ "month": 5, "year": 2026, "items": [
  { "budgetId": "…-uuid", "userId": "…-uuid", "category": "FOOD",
    "amountLimit": 12400000.00, "month": 5, "year": 2026,
    "spent": 2000000.00, "remaining": 10400000.00, "progressPercent": 16.13,
    "alertLevel": "SAFE", "createdAt": "…", "updatedAt": "…" }
]}
```
- After `RAISE_BUDGET` accept: the item's **`amountLimit`** equals the consented `resolvedValue`.
- After `CREATE_BUDGET` accept: a **new item** for that category appears (note: `spent` is
  backfilled from the month's existing expenses, so it may start non-zero).
- `alertLevel`: `SAFE` · `WARNING` · `EXCEEDED`.

**`GET /api/v1/projects/{projectId}`** → `data` (fields relevant to the contribute flow):
```json
{ "projectId": "…", "name": "Trip", "targetAmount": 30000000, "currency": "VND",
  "totalContributed": 5000000, "remaining": 25000000, "progressPercent": 16.67,
  "netSaved": 5000000, "deadline": "2026-12-31", "status": "ONGOING",
  "statusReason": "ON_TRACK", "monthsLeft": 7, "paceStatus": "ON_TRACK" }
```
- After `CONTRIBUTE_TO_PROJECT` accept: **`totalContributed`** / **`netSaved`** grow by
  `resolvedValue`; `remaining` / `progressPercent` move accordingly. This is also where you fetch
  the **project name** for the ask sentence (the payload carries only `projectId`).
- `status` is a display status (`ONGOING` · `OVERDUE` · `COMPLETED` · or a lifecycle state like
  `FROZEN`/`EXPIRED`/`ABANDONED`/`CANCELLED`); a contribution reaching the target flips it to
  `COMPLETED` synchronously.

### Still open

1. **Manual contribute button not built on the FE yet** (Screen 5). It's the primary way to fund a
   saving goal; until it exists, goals only gain progress via accepting a `CONTRIBUTE_TO_PROJECT` /
   `INCREASE_CONTRIBUTION` suggestion or the (off-by-default) settlement auto-save. The endpoint
   (`POST /projects/{id}/contributions`) is live — this is a FE build item, not a backend gap.
2. No `GET /suggestions/{id}` today — deep-link handler must fetch the list and filter. Add a
   by-id endpoint if the FE prefers.
3. Per-user **locale** for `narrative` isn't plumbed yet (defaults to English). i18n via `code` is
   the reliable path.
