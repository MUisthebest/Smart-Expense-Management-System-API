# Redesign — What the System Does and Why

> Companion to `redesign-technical-implementation.md`. This document captures the
> product direction and the rationale behind each decision. It supersedes the
> all-auto settlement model described in `project-tracking-all-auto-plan.md` and the
> Projects business rules in memory — see §5.

## 1. Direction in one sentence

Strip the heavy, automated financial constraints out of the mechanics, and move the
"smart" into a learning + suggestion layer — so the app is **simple to use, honest about
real money, and intelligent in what it surfaces**, rather than complex under the hood.

The three pivots below reinforce each other: simplifying the mechanics (A, B) is what
makes the intelligence layer (C) both necessary and safe.

## 2. Pivot A — Net income only (drop usable income / obligations)

### What changes
The user declares **one number: net income.** The `usable_income`, `safe_spending`, and
declared-"obligations" model is removed.

### Why
- **Lower onboarding friction** — one number instead of an income profile users get
  wrong anyway.
- The declared `usable_income` was the system's source of *conservatism* (money already
  spoken for). We don't delete that idea — we **learn** it instead: fixed/recurring costs
  are detected from real transactions (see Pivot C), giving a `disposable` figure that's
  grounded in behavior rather than self-report.

### What it affects
- The 20% project-saving rule loses its denominator. It is replaced by
  `disposable = net_income − learned recurring costs` and becomes **advisory, not
  enforced**.
- `safe_spending` disappears as a concept. Anything reading it must be redefined; with
  Groups and the settlement engine out of scope, the blast radius is small (mainly the
  project advisor and the AI data summary).
- `usable_income` / `safe_spending` columns go vestigial → keep nullable or drop via
  migration.
- **Trade-off accepted:** with only net income, naive math could let a user over-commit
  savings. The learned-disposable figure is the mitigation, which is why Pivot C is a
  prerequisite for doing this safely.

## 3. Pivot B — Press-to-contribute projects (drop the settlement engine)

### What changes
Projects stop being driven by an automated monthly settlement engine
("saving = not spending", debt, auto-freeze). Instead the user **presses a button to
contribute** toward a goal.

### Why
- The settlement engine is invisible, hard to demo, and hard to defend. A manual
  contribution is **honest, visible, and immediately understandable**.
- The domain already supports it — `Project.addContribution(...)` exists and works;
  this is largely a revert, not a green-field build.

### What it affects
- **Completion** happens synchronously when a contribution reaches the target (no cron).
- **Expiry** (deadline passed under target) is evaluated lazily on read (no cron).
- The big design decision: a contribution should move **real money**, not just a tally.
  Pressing "contribute" creates a real ledger transaction (envelope-budgeting style), so
  the project balance can never be fiction disconnected from the user's actual money —
  and the intelligence layer sees savings as just another transaction.
- The whole debt/arrears/auto-invest apparatus is removed → large simplification.

## 4. Pivot C — Insights + actionable yes/no suggestions (the "smart" layer)

### What it does
1. A deterministic engine continuously derives **insights** from the user's own history:
   - "This month's FOOD spend is unusual for you" (vs your rolling baseline).
   - "At this pace you'll exceed your food budget by the 22nd."
   - "These look like recurring/fixed costs" → your real obligations.
   - "Given your net income and real spending, you can realistically save X/month."
2. High-value insights are pushed as **yes/no notifications**:
   *"Your food budget keeps getting exceeded — raise it to 2.5M? [Yes] [No]"*
   Tapping **Yes** performs the action; **No** dismisses it.

### Why this design
- **Explainability over sophistication.** The findings come from simple, defensible
  statistics (rolling mean/stddev, medians, pace projection), not a black box. For a
  capstone you must defend, "we used statistics because of cold-start and tiny per-user
  data" is a *stronger* answer than "we used ML."
- **The LLM narrates, it does not decide.** The math is deterministic and verifiable; the
  language model only phrases the result. Cheaper, more accurate, and degrades gracefully.
- **Yes/No turns insight into action.** A suggestion the user can act on in one tap is the
  difference between a dashboard and an assistant.

### The learning loop
A dismissed suggestion is a labeled signal ("this advice was wrong for me"). Over time the
system suppresses advice the user keeps rejecting. This is real adaptivity implemented as
a counter + suppression rule — no RL, no bandit — and it is the honest answer to "how does
it learn the user's habits?"

### The key risk: notification fatigue
A yes/no system that pings too often gets muted, and then the feature is dead. The primary
engineering effort goes into **fatigue control** (dedup, severity gating, cooldowns), not
into fancier statistics. This is treated as the central risk, not an afterthought.

## 5. Pivot D — Trait-based cold start (replace the financial profile)

### What changes
The `UserFinancialProfile` — nine required qualitative fields (role, livingStatus,
incomeLevel, transportMode, spendingStyle, workStyle, familyStatus, studyIntensity,
healthNeed) — is removed. New users instead answer **three questions** during onboarding:

1. **How hard should I push you toward your goals?** — Relaxed / Steady / Aggressive
2. **When you're overspending, what helps?** — Just tell me / Nudge me / Hold me to a hard limit
3. **What's your focus right now?** — Build savings / Cut waste / Just stay aware

### Why
- **The old profile was inert.** Its nine fields were only ever dumped into an LLM prompt;
  nothing in the system acted on them. That is data you *collect*, not data you *use*.
- **The three traits are functional.** Each maps to a real knob in the suggestion engine
  (see the technical companion §9). That's the difference between decoration and control.
- **It solves cold start.** A brand-new user has no history for the analyzer to learn from.
  The traits provide sensible *defaults* on day one; behavior takes over as it accumulates.

### The principle behind it
Separate **what the system can learn** from **what it must ask.** Behavior tells you what a
user *does* (spending patterns, fixed costs) — learnable. It can never tell you how hard
they *want* to be pushed, what help they want, or their current focus — those are
preferences, not facts. So the traits are **orthogonal to the learning layer and
permanent**, not something behavior makes redundant.

### How it connects to Pivot C
Trait #2 (overspend help) sets the **cold-start default** for notification aggressiveness;
the dismissal feedback loop from Pivot C then **fine-tunes** it. Same knob — the trait is
the prior, dismissals are the online update. Trait #1 (goal push) generalizes and replaces
the per-project `ProjectBudgetAdvisorRequest.Mode` (URGENT/RELAXED) with a user default.

### Honest caveat
A passive tracker cannot physically *block* spending (money is already spent when logged).
"Hold me to a hard limit" therefore means **maximum nudge intensity** (immediate CRITICAL
alerts, aggressive lower-the-budget suggestions), not prevention. Onboarding copy must not
over-promise enforcement.

## 6. How the pivots interlock

- Pivot A removes declared obligations → Pivot C **re-derives** them from behavior, so the
  system stays conservative without asking the user.
- Pivot B grounds project progress in real transactions → Pivot C gets a clean savings
  signal for free.
- Pivot D provides the **cold-start priors** Pivot C needs before any history exists, and
  the dismissal loop refines them over time.
- Net result: **less hidden machinery, more visible intelligence.** The "smart" lives in a
  layer you can show and explain, not in a cron job nobody sees run.

## 7. Documentation status

This redesign **supersedes**:
- `docs/project-tracking-all-auto-plan.md` (the all-auto settlement model).
- The "All-Auto Settlement Model" and usable-income/obligation rules recorded in project
  memory.

Those describe the *previous* architecture. Until the redesign ships, treat this document
and its technical companion as the intended direction, and the older docs as historical.
</content>
