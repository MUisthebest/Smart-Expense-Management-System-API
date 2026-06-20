# Project Tracking UI — FE Handover

Companion to `docs/ui-contract-changes.md`. That doc covers the **breaking/semantic** API
changes from the all-auto redesign (removed contributions, `totalContributed` = net saved,
`EXPIRED` status). **This doc covers the enrichment layer** needed to build a project-tracking
experience that explains *why* a project moved, froze, or expired — not just the raw numbers.

> **Exact endpoints + JSON payloads** for everything below are in
> **`docs/project-tracking-api-contract.md`** — build against that.

> **Mental model the UI must teach:** the user never deposits money. Each month a cron
> ("settlement") reserves their monthly saving automatically if they stayed within budget,
> turns overspend into **debt** that eats progress, and freezes/expires projects on its own.
> The headline number is **`netSaved` (= moneySaved − moneyOwed)**, never gross `moneySaved`.

---

## Part A — Backend API additions (Phase 0) — ✅ IMPLEMENTED

These are derived/computed fields added to existing responses. **All display copy and i18n is the
FE's job** — the backend emits machine-readable enums + numbers, the FE composes the sentences.
This keeps the contract localization-friendly and testable.

> **Status:** shipped. New enums live in `application.dtos.responses.project`
> (`PaceStatus`, `ProjectStatusReason`, `HistoryOutcome`). Unit tests:
> `ProjectTrackingResponseTest`, `ProjectDetailResponseStatusReasonTest`, `ProjectHistoryResponseTest`.
> No breaking changes — all fields are additive.

### A1. `ProjectTrackingResponse` — new fields

| Field | Type | Meaning / computation |
|---|---|---|
| `monthsToDeadline` | int | Calendar months from today to `deadline` (ceil, min 0). |
| `paceStatus` | enum | `AHEAD` / `ON_TRACK` / `BEHIND` / `NOT_APPLICABLE`. Compares pace-aware `monthLeft` to `monthsToDeadline`: `monthLeft < monthsToDeadline` → `AHEAD`; `==` → `ON_TRACK`; `>` → `BEHIND`; terminal/no-deadline → `NOT_APPLICABLE`. |
| `maxFrozenMonths` | int | The config cap (`app.project.max-frozen-months`, default 3). Lets FE render `frozenMonths`/`maxFrozenMonths` ("Frozen 2/3"). |
| `debtClearEstimateMonths` | int \| null | If `moneyOwed > 0` and `monthlySaving > 0`: `ceil(moneyOwed / monthlySaving)`. Else `null`. **Heuristic** — assumes a full monthly surplus repays debt; label it as an estimate ("~2 months"). |

`moneyOwed`, `netSaved`, `frozenMonths` already exist (added in the redesign — see ui-contract §4).

**Sketch** (`ProjectTrackingResponse.fromDomain` gains a `deadline`/`config` arg, or compute in the
use case which already loads the `Project`):

```java
// new fields
private Integer monthsToDeadline;
private PaceStatus paceStatus;          // AHEAD | ON_TRACK | BEHIND | NOT_APPLICABLE
private Integer maxFrozenMonths;
private Integer debtClearEstimateMonths; // nullable
```

`GetProjectTrackingUseCase` already loads both the `Project` (has `deadline`, `status`) and the
`ProjectTracking`, so it can compute `monthsToDeadline`/`paceStatus` and inject `maxFrozenMonths`
from `@Value("${app.project.max-frozen-months:3}")`. Prefer computing there over fattening the
static `fromDomain`.

### A2. `ProjectDetailResponse` — status reason

`status`, `statusLabel`, `monthsLeft`, `moneyOwed`, `netSaved` already exist. Add a **machine
reason** so the FE can show an explanation, not just a badge:

| Field | Type | Meaning |
|---|---|---|
| `statusReason` | enum | `ON_TRACK`, `BEHIND_PACE`, `FROZEN_DEBT`, `EXPIRED_DEADLINE`, `EXPIRED_FROZEN_TOO_LONG`, `COMPLETED`, `ABANDONED_BY_USER`, `NONE`. |

Derivation (in `ProjectDetailResponse.fromDomain`, all from data already in hand):
- `COMPLETED` → `COMPLETED`
- `ABANDONED` → `ABANDONED_BY_USER`
- `FROZEN` → `FROZEN_DEBT`
- `EXPIRED` → `EXPIRED_DEADLINE` if `monthLeft <= 0`, else `EXPIRED_FROZEN_TOO_LONG` *(best-effort
  inference — only `status` is persisted, not the cause)*
- live + `paceStatus == BEHIND` → `BEHIND_PACE`; else `ON_TRACK`

### A3. `ProjectHistoryResponse` — per-month outcome

All numeric fields already exist. Add one classifier so the FE can render the right icon + sentence:

| Field | Type | Meaning |
|---|---|---|
| `outcome` | enum | `CLEAN_MONTH` / `OVERSPENT` / `UNDERSPENT_BONUS` / `FROZEN_NO_SAVING` |
| `netChange` | BigDecimal | `moneySavedAfter − moneySavedBefore` (convenience; FE can also compute). |

Derivation: `penalty > 0` → `OVERSPENT`; else `surplusInvested > 0` → `UNDERSPENT_BONUS`; else
`moneySavedAfter > moneySavedBefore` → `CLEAN_MONTH`; else `FROZEN_NO_SAVING`.

### A4. Group project (if in scope) — see ui-contract §4

`requiredTarget` (dynamic), per-member `netSaved`/`moneyOwed` already specified there. The pace/
outcome enums above apply per sub-project too.

---

## Part B — FE implementation plan

### B0. Status → color/copy maps (do this first; everything depends on it)
- **Status badge map** must cover all six `ProjectStatus` values incl. `EXPIRED` (ui-contract §3),
  plus the derived display statuses `ONGOING`/`OVERDUE`/`COMPLETED` from `ProjectDetailResponse.status`.
- **`statusReason` → sentence map** (the "why" copy).
- **`paceStatus` → label/color map** (AHEAD=green, ON_TRACK=neutral, BEHIND=amber).
- **`outcome` → icon + sentence template** for history rows.
- Remove all contribution UI (button, list, summary) — those endpoints 404 now (ui-contract §1).

### B1. Project hero card (`ProjectDetailResponse` + `/tracking`)
- Headline = **`netSaved` / `targetAmount`**, progress bar = `progressPercent`.
- If `moneyOwed > 0`: render a distinct **red overlay segment** on the bar and a "−$X debt" caption.
  This is the key teaching moment — it explains the gap between reserved and net.
- Sub-line: `statusLabel` + `paceStatus` chip + "reserving $`monthlySaving`/mo · deadline `deadline`".
- **Acceptance:** a project with debt shows net < gross visibly; pace chip reflects `paceStatus`.

### B2. Status / reason banner
- Show `statusReason` copy contextually:
  - `FROZEN_DEBT` → "Paused — overspend debt hit the limit. Auto-resumes when debt clears.
    Frozen `frozenMonths`/`maxFrozenMonths` months." (amber; emphasize the countdown — at the cap it expires.)
  - `EXPIRED_*` / `ABANDONED_BY_USER` → terminal styling, no resume/edit actions.
  - `BEHIND_PACE` → gentle nudge.
- **Acceptance:** every non-ACTIVE state shows a human reason, never a bare enum.

### B3. Debt panel (only when `moneyOwed > 0`)
- "Overspend debt: $`moneyOwed`. Subtracted from progress; auto-clears when you spend under budget.
  ~`debtClearEstimateMonths` months to clear at current pace."
- Frame as *recoverable* (matches engine: surplus repays debt before investing).
- **Acceptance:** hidden when `moneyOwed == 0`; estimate shown only when non-null.

### B4. Monthly history timeline (`/{projectId}/history`)
- Vertical list, **newest first** (sort by `year`,`month` desc).
- Each row driven by `outcome`: icon + `moneySavedBefore → moneySavedAfter`, and the relevant
  amount (`penalty` for OVERSPENT, `surplusInvested` for UNDERSPENT_BONUS).
- Optional sparkline of `moneySavedAfter` across months above the list.
- **Note:** `/history` requires **manage** access (owner-only); `/tracking` only needs view. Don't
  show the timeline on a shared/member view of someone else's project.
- **Acceptance:** the three outcome types each render a distinct, correct sentence.

### B5. Group project view (if in scope, B-tier)
- Progress off **`requiredTarget`** not `targetAmount` (shrinks as members drop). Keep `targetAmount`
  as "Original goal".
- Member roster: `netSaved` vs target per member, debt flag, dropped-out (EXPIRED/ABANDONED) styling.
- Handle group `EXPIRED` badge + the two new notifications (member-dropped, group-expired).

### Build order / phasing
1. **Phase 0 (backend):** A1–A3 fields + enums. Small, additive, no breaking changes.
2. **Phase 1 (FE foundation):** B0 maps + remove contribution UI + B1 hero card.
3. **Phase 2 (FE comprehension):** B2 reason banner + B3 debt panel + B4 history timeline.
4. **Phase 3 (FE group):** B5, if group projects are in this milestone.

### Resolved decisions
- **Manual freeze/unfreeze button:** ❌ **Hidden.** Freeze is system-driven (debt cap → freeze,
  debt cleared → auto-unfreeze). The `/freeze` and `/unfreeze` endpoints remain live server-side but
  the UI must not surface them for personal projects.
- **Narrative copy location:** ✅ **FE composes.** Backend emits machine-readable enums + numbers
  (`paceStatus`, `statusReason`, `outcome`); the FE owns all sentences and i18n.
