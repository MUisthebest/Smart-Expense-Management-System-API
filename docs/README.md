# Docs reading map

Not every document in this folder describes the current system. If you are building against the
API today (especially the **FE agent** building the insights/suggestions experience), use this map.

## Start here (current, authoritative)

| Doc | What it is |
|---|---|
| [`frontend/adaptive-engine-fe-guide.md`](frontend/adaptive-engine-fe-guide.md) | **The FE contract** for the insight → suggestion loop: endpoints, payloads, enums, i18n keys, screen checklist. |
| [`frontend/i18n.en.reference.json`](frontend/i18n.en.reference.json) | Starter English copy for every i18n key the backend emits — one template per key, placeholders named after the actual metric/payload fields. |
| [`frontend/openapi.json`](frontend/openapi.json) *(generated)* | Full machine-readable API spec exported from the running server (`/v3/api-docs`). Covers everything beyond the guide's scope (transactions, budgets, projects, auth). Regenerate with `scripts/capture-fe-handoff.ps1`. |
| [`frontend/fixtures/`](frontend/fixtures/) *(generated)* | Real captured JSON responses from a seeded run — use as mock data. Regenerate with `scripts/capture-fe-handoff.ps1` after running the api-testing collection. |
| [`api-testing/`](api-testing/README.md) | Runnable Postman/Bruno collection that drives the whole loop end-to-end against a live server. The FE guide was authored from this flow. |
| [`redesign-what-and-why.md`](redesign-what-and-why.md) | **The product rationale.** Read this to understand *why* the system behaves as it does (never moves money on its own; one yes/no ask; fatigue promises; press-to-contribute projects). |

## Background / internal (current, but you don't need them to build the FE)

- `redesign-technical-implementation.md` — backend design decisions for the redesign.
- `redesign-phase1-plan.md`, `redesign-phase2-3-plan.md` — implementation phase plans (all shipped).
- `redesign-workstream-c-plan.md`, `redesign-workstream-c-implementation.md` — the insight/suggestion workstream in detail.
- `redesign-projects-and-categories-food-for-thought.md` — design exploration notes.

## ⚠️ Superseded — do NOT use as a behavior reference

These describe the **old all-auto settlement system** that the redesign replaced. They contradict
the current business rules (auto-contributions, monthly settlement, debt/freeze mechanics, safe
spending). Kept for history only.

- `project-tracking-all-auto-plan.md`
- `project-tracking-api-contract.md`
- `project-tracking-ui-handover.md`

If anything in a superseded doc conflicts with the FE guide or `redesign-what-and-why.md`, the
newer docs win.
