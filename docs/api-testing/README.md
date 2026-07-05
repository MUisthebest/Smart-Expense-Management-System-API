# Adaptive-Engine API Test Collection

A user-driven end-to-end run of the redesign's **insight → suggestion** loop, importable into
**Postman** or **Bruno** (Postman v2.1 schema). It walks the exact flow the mobile app will drive:

> onboard traits → seed trailing history → trigger an insight → receive a yes/no suggestion →
> accept/dismiss → verify the side effect.

- `adaptive-engine.postman_collection.json` — the ordered flow, with test scripts that capture the
  token / IDs and assert each step.
- `adaptive-engine.postman_environment.json` — `baseUrl` + the test account. Edit these two.

The companion **FE contract** built from this flow is
[`docs/frontend/adaptive-engine-fe-guide.md`](../frontend/adaptive-engine-fe-guide.md).

---

## Why the collection is shaped the way it is (read this first)

Suggestions are **not** a normal endpoint — they are a *side effect* of creating a transaction.
Three code facts drive the whole design:

1. **The producer analyzes `asOf = the transaction's own date`**
   (`CreateTransactionUseCase` → `SuggestionProducer`). So the scenario is fully time-shiftable —
   this collection dates the whole story in a **fixed past month**: baseline in Feb/Mar/Apr 2026,
   trigger on **15 May 2026**. Because nothing here reads the real clock, the suggestion flow fires
   identically whatever the real calendar date is — you can run it (or demo it) early in the month.
2. **`GET /insights` analyzes `LocalDate.now()`** by default, so it wouldn't see the seeded May data
   today. The insight requests therefore pass **`?asOf=2026-05-15`** (the deterministic overload,
   surfaced as an optional query param — a testing/demo aid) so the pull list matches the scenario.
3. **Insights need ≥3 trailing months** of history (z-score baseline, trend, recurring). → The
   `1 — Seed trailing baseline` folder posts three prior months (Feb/Mar/Apr) before any trigger.
4. **The pace forecast has a `MIN_PACE_DAYS = 3` guard** and budget suggestions are **income-capped**.
   The trigger is on day 15 (past the guard); the FOOD raise (~4.13M projected vs a 3M limit) is well
   under the 20M income, so no cap binds — see the RAISE_BUDGET folder.

## Prerequisites

| # | Requirement | Why |
|---|---|---|
| 1 | App running on `baseUrl` (default `http://localhost:8080`) with its DB/migrations applied | live path |
| 2 | A **pre-verified** account in `email`/`password` | registration needs multipart + an email OTP (`/auth/verify-email`) that can't be automated here — register once by hand, verify, then reuse |
| 3 | Trait `interventionLevel = GENTLE` (the collection sets this) | `NOTIFY`/null = pull-only → **no suggestions are ever pushed** |
| 4 | **Recommended test profile:** `app.suggestion.daily-cap=0` and `app.suggestion.cooldown-hours=0` | otherwise the P3-A fatigue ceiling (default cap 3 / 24h cooldown) can legitimately suppress the 2nd–3rd suggestion in a single run and make the flow look broken |
| 5 | `app.demo-triggers.enabled=true` (env `DEMO_TRIGGERS_ENABLED=true`) — **for folders 13–18** | the Wave 3/4 recap, sweep and project-health flows are scheduler-driven; the demo endpoints (`POST /api/v1/demo/adaptive/recap\|sweep\|project-health?asOf=…`) run the same code paths on demand. Without the flag the endpoints don't exist (404) |

> The cap/cooldown use the **real** clock and count suggestion rows created *today*, independent of
> the (back-dated) transaction dates. That is correct production behaviour — it just needs to be
> turned off to see every functional path in one sitting. Set via env vars / `application.yml`:
> ```yaml
> app:
>   suggestion:
>     daily-cap: 0        # 0 = unlimited (test only)
>     cooldown-hours: 0   # 0 = no per-topic cooldown (test only)
> ```

## Run order

Run the folders **top to bottom** (Postman Collection Runner, or Bruno's run). Each folder depends on
state from the previous one:

| Folder | What it proves |
|---|---|
| `0 — Setup` | login (captures token), traits set to GENTLE/BALANCED/REDUCE_SPENDING |
| `1 — Seed trailing baseline` | 3 months of FOOD (flat), ENTERTAINMENT (varying), INCOME — stays quiet, no premature suggestion |
| `2 — RAISE_BUDGET` | budgeted category over pace → `BUDGET_PACE` → `RAISE_BUDGET` → accept raises the limit |
| `3 — CREATE_BUDGET` | un-budgeted category spikes → `OVERSPEND_ANOMALY` → `CREATE_BUDGET` → accept creates a budget |
| `4 — CONTRIBUTE_TO_PROJECT` | income + headroom → `SAVINGS_CAPACITY` → `CONTRIBUTE_TO_PROJECT` → accept records a contribution |
| `5 — Idempotency & dismissal` | re-responding does not re-run the action |
| `6 — Notifications` | a push row carries the `app://suggestions/{id}` deep-link |
| `7 — SET_CATEGORY_LIMIT` | un-budgeted category trending up (not an anomaly) → `CATEGORY_TREND` → `SET_CATEGORY_LIMIT` → accept creates a first budget |
| `8 — REDUCE_BUDGET` | budgeted category consistently under limit → `BUDGET_UNDERSPEND` → `REDUCE_BUDGET` → accept lowers the limit |
| `9 — LARGE_TRANSACTION` | a per-charge outlier expense → notification-only ping (`app://transactions`), no suggestion |
| `10 — DUPLICATE_CHARGE` | same category+amount+description within 72h → notification-only ping (`app://transactions`), no suggestion |
| `11 — REBALANCE_BUDGETS` | a budget **write** pushes committed limits over declared income → `BUDGET_OVER_INCOME` → `REBALANCE_BUDGETS` (per-budget snapshot) → accept scales every listed limit down to fit |
| `12 — REVIEW_SUBSCRIPTION` | a stable recurring charge posts above its tolerance band → `SUBSCRIPTION_PRICE_HIKE` → acknowledge-only `REVIEW_SUBSCRIPTION` (no `proposedAction`, accept executes nothing) |
| `13 — Monthly recap` | the closed month is judged at rollover: July → `CASHFLOW_NEGATIVE` ping; August (no income logged!) → `INCOME_DROP` ping. Demo endpoint runs the scheduler's exact code path |
| `14 — Month-end sweep` | a `CONTRIBUTE_TO_PROJECT` ask **without any income event** — the day-25 sweep nudges from learned income + headroom. Demo endpoint, same producer the cron calls |
| `15 — INCREASE_CONTRIBUTION` | a saving project behind the pace its deadline requires → `PROJECT_OFF_TRACK` (CRITICAL when ≥2× behind or stalled) → catch-up ask sized `remaining ÷ months left` → accept records the contribution. **Dynamic dates** (see below) |
| `16 — PROJECT_MILESTONE` | positive ping when a contribution crosses 25/50/75/100% progress (deep link `app://projects/{id}`); a tiny non-crossing top-up proves each milestone is celebrated **once** |
| `17 — GOOD_MONTH` | a quiet, budgeted October closes with every budget under its limit → one cheer (`app://budgets`) riding the recap; re-running the recap proves the once-per-month marker |
| `18 — CREATE_SAVING_PROJECT nudge` | completes/deletes the projects (also proving a 2-threshold jump celebrates only the highest milestone), then the sweep finds capacity with **no active project** → deep-link nudge to the pre-filled create form (no suggestion card — item 10's resolved shape) |
| `19 — REALLOCATE_BUDGET` | budgeted category over pace **plus** another budget confirmed underspent → `REALLOCATE_BUDGET` **instead of** `RAISE_BUDGET`: a zero-sum snapshot that raises the overflowing budget and lowers the underused one by the same amount → accept applies both atomically. Uses the fixed demo window (baseline in the seed months, trigger 15 July); **run on a fresh account** (its FOOD/ENTERTAINMENT July budgets collide with folders 2–3) |

Folders 7–10 (Wave 1 of the expansion plan) each **seed their own disjoint categories** (TRANSPORTATION,
CLOTHING, SHOPPING, UTILITIES), so they don't collide with the FOOD/ENTERTAINMENT state from folders 1–4.
Folders 9 and 10 are **notification-only** — there is no suggestion card or accept step; they assert the
push landed. Their content is the raw i18n key (e.g. `notification.insight.large_transaction|SHOPPING`)
**only when server narration is off** — with narration on, assert on the `app://transactions` deep-link
instead of the key substring.

Folders 13–14 (Wave 3) exercise the **scheduler-driven** flows deterministically: in production
`MonthlyRecapScheduler` (cron `app.recap.cron`, default 09:00 on the 1st) and `MonthEndSweepScheduler`
(`app.sweep.cron`, default 09:00 on the 25th) fan these out to all eligible users; the demo endpoints
run the identical per-user use case for the authenticated account with an explicit `asOf`. They need
**no new seeds** — they read the world folders 0–12 already built (so run those first): July closed
over-spent, and August has zero income against a flat 20M baseline. To watch a cron fire for real,
set `app.recap.enabled=true` and `RECAP_CRON="0 * * * * *"` (every minute) in a demo profile.

Folders 11–12 (Wave 2) differ from everything before them in two ways. **Folder 11's trigger is a budget
write, not a transaction** — `Create/UpdateBudgetUseCase` now runs the rebalance check after saving, so
the suggestion appears the moment the user over-commits, and it must run **after folders 0–8** (it needs
their accumulated budgets plus the 20M HEALTH budget to exceed the 20M income). Its accept is the first
**multi-entity** action: every budget in `proposedAction.budgetAdjustments` is set to its snapshotted
`newLimit`. **Folder 12 is acknowledge-only** — the suggestion card exists and is accepted like any
other, but the payload has no `proposedAction` and accepting changes no data (EDUCATION is its disjoint
category).

Folders 15–18 (Wave 4) close out the plan. **Folder 15 is the one deviation from the fixed-2026
dating**: a project's `createdAt` is the real server clock (the API sets it), and the off-track
judgment needs the project to be at least a month old — so its pre-request script computes dates
relative to the real "today" (deadline = the 15th three months out; judged `asOf` the 1st of next
month). The math stays exactly deterministic (remaining 11M ÷ 3 months → a `3,666,666.67` ask).
Folders 16–18 are the **positive/nudge** paths: no suggestion cards — they assert on notifications
(milestone pings deep-linked to the project, the GOOD_MONTH cheer to `app://budgets`, and the
create-project nudge to `app://projects/create?amount=<seed>`). Folder 18 must run **after 15–17**: it
completes the folder-15 project and deletes the folder-4 one to reach the "capacity but no active
project" state item 10 targets. Like folders 9–13, their key-substring asserts assume **server
narration is off**; with narration on, assert the deep links instead.

Folder 19 (`REALLOCATE_BUDGET`, UC1) follows the collection's **fixed May-window convention** like the
other suggestion folders: it seeds a FOOD/ENTERTAINMENT baseline across the three seed months and
triggers on 15 July 2026. Because those are the same categories folders 2–3 budget in July, run it on
a **fresh account** (folder 0 first) rather than in the same pass as folders 2–3 — otherwise the July
FOOD/ENTERTAINMENT budgets already exist and the create steps 409. The reallocation is **preferred
over a raise**: the producer offers it whenever the underspent donor budgets can *fully* cover the
overflowing budget's gap, and only falls back to `RAISE_BUDGET` when they can't. Accept applies the
multi-budget snapshot through the same executor as `REBALANCE_BUDGETS`, with a guard that rejects the
whole ask if any lowered limit would fall below what that budget has already spent.

Because the scenario is pinned to **May 2026**, re-running on the same account hits the
one-open-ask-per-topic **dedup** (a 2nd PENDING for the same topic is refused) and the accepted
budgets already exist. For a clean re-run, use a **fresh account**, or first respond to / delete the
open suggestion rows. This is by design (fatigue control), not a bug.

## Known soft spots (verify against your build)

These assertions are intentionally loose because the exact list/detail DTO field names weren't pinned
while authoring — tighten them after the first green run:

- **`GET /budgets`** (folder 2, last request): the list wrapper may be `data.budgets[]` or `data[]`;
  the test only checks `data` exists. Add a concrete "FOOD limit > 10,000,000" assertion once you
  confirm the shape.
- **Project create** (folder 4): `priority` uses `MEDIUM` — adjust if your `ProjectPriority` enum
  differs (or drop the field, it's optional). `projectId` is read from `data.id`/`data.projectId`.
- **`GET /projects/{id}`**: asserts presence only; add a concrete "contributed/netSaved increased"
  check against the real `ProjectDetailResponse` field.

## Testing the fatigue layer on purpose (optional)

The happy path turns fatigue **off**. To prove it works, set `cooldown-hours` back to `24` (or
`daily-cap=1`) and re-trigger the same topic — the producer will skip the 2nd push. To prove
**dismissal suppression** (P2-A): dismiss the same topic twice (re-triggering between, with cooldown
off), then trigger a third time — no new suggestion appears unless the severity escalates to
`CRITICAL`, which always breaks through.
