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
