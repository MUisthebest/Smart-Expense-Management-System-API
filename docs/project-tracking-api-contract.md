# Project Tracking — API Contract (FE)

Concrete endpoint + payload reference for the UI in `docs/project-tracking-ui-handover.md`.
All shapes reflect the **Phase 0 enrichment (shipped)**. Field names are the exact JSON keys
(camelCase). See also `docs/ui-contract-changes.md` for the breaking/semantic changes.

## Conventions

- **Base path:** `/api/v1/projects`
- **Auth:** `Authorization: Bearer <JWT>` on every request.
- **Response envelope** — every endpoint wraps its payload in `CheckResponse<T>`:
  ```json
  { "success": true, "message": "OK", "data": { /* T */ }, "errorCode": null }
  ```
  `errorCode`/`data` are omitted when null (`@JsonInclude(NON_NULL)`). On failure:
  ```json
  { "success": false, "message": "Project not found", "errorCode": "PROJECT_NOT_FOUND" }
  ```
- **Money** fields are decimal strings/numbers with 2dp scale. **Dates** are ISO (`LocalDate` =
  `YYYY-MM-DD`; `Instant` = ISO-8601 UTC).

## New enums (Phase 0)

| Enum | Values |
|---|---|
| `paceStatus` | `AHEAD`, `ON_TRACK`, `BEHIND`, `NOT_APPLICABLE` |
| `statusReason` | `ON_TRACK`, `BEHIND_PACE`, `FROZEN_DEBT`, `EXPIRED_DEADLINE`, `EXPIRED_FROZEN_TOO_LONG`, `COMPLETED`, `ABANDONED_BY_USER`, `NONE` |
| `outcome` (history) | `CLEAN_MONTH`, `OVERSPENT`, `UNDERSPENT_BONUS`, `FROZEN_NO_SAVING` |
| `status` (domain) | `ACTIVE`, `FROZEN`, `COMPLETED`, `EXPIRED`, `ABANDONED`, `CANCELLED` (deprecated) |

> The FE owns all display copy/i18n — map these enums to labels, colours, and sentences.

---

## 1. List my projects — card grid

`GET /api/v1/projects` · query (all optional): `search`, `type`, `status`, `priority`

`data: ProjectListItemResponse[]`

| Field | Type | Notes |
|---|---|---|
| `projectId` | string (UUID) | |
| `name` | string | |
| `type` | enum | `PERSONAL` |
| `priority` | enum | `LOW`/`MEDIUM`/`HIGH` |
| `status` | enum | domain status (handle `EXPIRED`) |
| `targetAmount` | number | |
| `currency` | string | e.g. `USD` |
| `totalContributed` | number | **= net saved** (`moneySaved − moneyOwed`), not deposits |
| `remaining` | number | `target − totalContributed`, floored at 0 |
| `progressPercent` | number | 0–100, drives the bar |
| `deadline` | date | |
| `monthsLeft` | int | calendar months; `0` when completed/overdue |
| `deadlineLabel` | string | server-provided convenience (`"6 months"`, `"Completed"`, `"Overdue"`) |

> List items do **not** carry the pace/debt enrichment — fetch detail/tracking for that.

---

## 2. Project detail — hero card + status banner

`GET /api/v1/projects/{projectId}` · `data: ProjectDetailResponse`

| Field | Type | Notes |
|---|---|---|
| `projectId`, `ownerId` | string (UUID) | |
| `type`, `groupProjectId` | enum, UUID\|null | `groupProjectId` set for sub-personal projects |
| `name`, `description` | string | |
| `targetAmount`, `currency` | number, string | |
| `totalContributed` | number | net saved (source of truth for progress) |
| `netSaved` | number | same value, explicit name |
| `moneyOwed` | number | overspend debt; show when `> 0` (red bar segment) |
| `remaining` | number | |
| `progressPercent` | number | 0–100 |
| `deadline` | date | |
| `status` | string | display status: `ONGOING`/`COMPLETED`/`OVERDUE` while live, else the real domain status (`FROZEN`/`EXPIRED`/`ABANDONED`/`CANCELLED`) |
| **`statusReason`** | enum | **NEW** — the "why" behind the badge (see table above) |
| `monthsLeft` | int | **calendar** months to deadline (deadline countdown; drives the labels) |
| **`paceMonthsLeft`** | int\|null | **NEW** — pace-aware months left (from tracking); `null` when no tracking. Differs from `monthsLeft` when ahead/behind |
| **`paceStatus`** | enum | **NEW** — `paceMonthsLeft` vs `monthsLeft` (`AHEAD`/`ON_TRACK`/`BEHIND`/`NOT_APPLICABLE`) |
| `deadlineLabel`, `statusLabel` | string | convenience labels (built from `monthsLeft`) |
| `priority` | enum | |
| `monthlySaving` | number | amount reserved per month |
| `currentMonth` | int | settlement month counter |
| `durationMonths` | int | total planned months |
| `createdAt` | instant | |
| `histories` | array | embedded `ProjectHistoryResponse[]` (same shape as §4) |

**Example**
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "projectId": "8f3b...", "ownerId": "12ab...", "type": "PERSONAL", "groupProjectId": null,
    "name": "Bali Trip", "description": "Year-end trip",
    "targetAmount": 3000.00, "currency": "USD",
    "totalContributed": 1200.00, "netSaved": 1200.00, "moneyOwed": 360.00,
    "remaining": 1800.00, "progressPercent": 40.00,
    "deadline": "2026-11-01",
    "status": "ONGOING", "statusReason": "BEHIND_PACE",
    "monthsLeft": 5, "paceMonthsLeft": 6, "paceStatus": "BEHIND",
    "deadlineLabel": "5 months", "statusLabel": "5 months left",
    "priority": "HIGH", "monthlySaving": 360.00, "currentMonth": 3, "durationMonths": 8,
    "createdAt": "2026-03-01T00:00:00Z",
    "histories": [ /* see §4 */ ]
  }
}
```

---

## 3. Project tracking — pace, debt, frozen countdown

`GET /api/v1/projects/{projectId}/tracking` · `data: ProjectTrackingResponse`
(view access; non-owner viewers allowed)

| Field | Type | Notes |
|---|---|---|
| `id`, `projectId` | string (UUID) | |
| `moneySaved` | number | gross reserved |
| `moneyOwed` | number | overspend debt |
| `netSaved` | number | `moneySaved − moneyOwed`, floored at 0 |
| `currentMonth` | int | |
| `monthlySaving` | number | |
| `monthLeft` | int | **pace-aware** months left (settlement-recalculated) |
| `frozenMonths` | int | consecutive frozen months |
| **`monthsToDeadline`** | int\|null | **NEW** — calendar months to deadline |
| **`paceStatus`** | enum | **NEW** — `monthLeft` vs `monthsToDeadline` |
| **`maxFrozenMonths`** | int | **NEW** — cap; render `frozenMonths`/`maxFrozenMonths` (at cap → expires) |
| **`debtClearEstimateMonths`** | int\|null | **NEW** — `ceil(moneyOwed / monthlySaving)`; `null` when no debt. Show as "~N months" (estimate) |
| `createdAt`, `updatedAt` | instant | |

**Example**
```json
{
  "success": true, "message": "OK",
  "data": {
    "id": "aa11...", "projectId": "8f3b...",
    "moneySaved": 1560.00, "moneyOwed": 360.00, "netSaved": 1200.00,
    "currentMonth": 3, "monthlySaving": 360.00, "monthLeft": 6, "frozenMonths": 0,
    "monthsToDeadline": 5, "paceStatus": "BEHIND", "maxFrozenMonths": 3,
    "debtClearEstimateMonths": 1,
    "createdAt": "2026-03-01T00:00:00Z", "updatedAt": "2026-06-01T00:00:05Z"
  }
}
```

> The create/update tracking endpoints (`POST`/`PUT /{projectId}/tracking`) return the **same**
> `ProjectTrackingResponse` shape — use the `GET` for the read screens.

---

## 4. Project history — monthly timeline

`GET /api/v1/projects/{projectId}/history` · `data: ProjectHistoryResponse[]`
**Owner-only** (manage access) — do not call on a shared/member view of someone else's project.
Sort client-side by (`year`,`month`) descending for newest-first.

| Field | Type | Notes |
|---|---|---|
| `id`, `projectId` | string (UUID) | |
| `year`, `month` | int | settlement period |
| `moneySavedBefore` / `moneySavedAfter` | number | savings around this settlement |
| `monthlySaving` | number | |
| `penalty` | number | overspend debt added this month (0 if none) |
| `surplusInvested` | number | bonus auto-invested this month (0 if none) |
| **`netChange`** | number | **NEW** — `moneySavedAfter − moneySavedBefore` |
| **`outcome`** | enum | **NEW** — `CLEAN_MONTH`/`OVERSPENT`/`UNDERSPENT_BONUS`/`FROZEN_NO_SAVING` |
| `monthLeftBefore` / `monthLeftAfter` | int | pace before/after |
| `createdAt` | instant | |

**Example**
```json
{
  "success": true, "message": "OK",
  "data": [
    { "id": "h3...", "projectId": "8f3b...", "year": 2026, "month": 5,
      "moneySavedBefore": 840.00, "moneySavedAfter": 840.00,
      "monthlySaving": 360.00, "penalty": 160.00, "surplusInvested": 0.00,
      "netChange": 0.00, "outcome": "OVERSPENT",
      "monthLeftBefore": 7, "monthLeftAfter": 6, "createdAt": "2026-06-01T00:00:05Z" },
    { "id": "h2...", "projectId": "8f3b...", "year": 2026, "month": 4,
      "moneySavedBefore": 480.00, "moneySavedAfter": 840.00,
      "monthlySaving": 360.00, "penalty": 0.00, "surplusInvested": 0.00,
      "netChange": 360.00, "outcome": "CLEAN_MONTH",
      "monthLeftBefore": 8, "monthLeftAfter": 7, "createdAt": "2026-05-01T00:00:05Z" }
  ]
}
```

---

## 5. Endpoints to NOT surface

| Endpoint | Why |
|---|---|
| `POST /{projectId}/freeze`, `POST /{projectId}/unfreeze` | Still live, but freeze is **system-driven** — hide the manual buttons. |
| `POST/GET /{projectId}/contributions`, `.../contributions/summary` | **Removed — 404.** No manual deposits in the all-auto model. |

## 6. Other lifecycle endpoints (unchanged, for reference)

| Method | Path | Returns |
|---|---|---|
| `POST` | `/api/v1/projects` | `ProjectDetailResponse` (create) |
| `PUT` | `/api/v1/projects/{projectId}` | `ProjectDetailResponse` (update info) |
| `DELETE` | `/api/v1/projects/{projectId}` | `void` — see note below |
| `POST` | `/api/v1/projects/project-advisor` | `ProjectBudgetAdvisorResponse` |

> **`DELETE` semantics:** for a **standalone** personal project this is now a **true hard delete**
> (it disappears — project, tracking, and history are removed). For a **sub-personal** project
> (`groupProjectId != null`) it instead abandons the project so the group stays consistent. Same
> request/response either way; label the button "Delete" and treat it as **irreversible**.

> Group-project endpoints (`requiredTarget`, per-member `netSaved`/`moneyOwed`) are covered in
> `docs/ui-contract-changes.md` §4 — needed only for the Phase 3 group view.
