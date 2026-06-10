# Hybrid Splitwise + Personal Project Debt Safeguards — Implementation Context

> Context export for resuming work in another chat session.
> Project: `Smart-Expense-Management-System-API` (Spring Boot 4.0.1, Java 17, PostgreSQL, Maven).

---

## 1. Original Goal

Add **debt safeguards** to the existing Projects feature so personal & group projects cannot quietly push a user into financial trouble. Implementation follows a **layered defense**:

1. **Preventive** — `SafeSpendingService.requireCanAfford(...)` gate on every action that increases a user's monthly financial obligation.
2. **Detective** — Monthly worker (`GroupDebtAssessmentWorker`) that accrues arrears and detects defaults.
3. **Corrective** — Project lifecycle states (`FROZEN`, `ABANDONED`), default-kick logic, and ledger reversal via `PROJECT_REFUND` transactions on abandonment.

---

## 2. Architectural Rules / Constants

| Rule | Value |
|---|---|
| Max members per GROUP project | `10` |
| "Early in the month" window | Days **1–7** |
| Default threshold | Member arrears `>= 3 × monthlyCommitment` |
| Worker cron | `0 30 0 1 * *` (00:30 on day 1, 30 min after monthly settlement at `0 0 0 1 * *`) |
| HTTP status for insufficient safe spending | **422 Unprocessable Entity** |
| Refund ledger category | `Category.PROJECT_REFUND` (income side) |
| Owner freeze on personal project | Triggers when `moneyOwed >= monthlySaving × 3` |

---

## 3. Project Stack & Conventions

- **Spring Boot 4.0.1 / Java 17 / Maven** — root package `com.example.smartmoney`
- **PostgreSQL + Flyway** migrations under `src/main/resources/db/migration/` (V1–V13)
- **JPA/Hibernate + MapStruct** — entities at `infrastructure/persistence/entities/`, mappers at `infrastructure/persistence/mappers/`
- **Clean Architecture / DDD / Hexagonal** — rich aggregates, value objects, factory methods
- **Redis Streams + WebSocketNotificationPublisher** for async events + realtime push
- **Scheduling** — `@Scheduled` on `@Component` workers under `infrastructure/worker/`
- **Error handling** — `GlobalExceptionHandler` with per-exception `@ExceptionHandler` methods returning `CheckResponse<>`

---

## 4. Implementation Status — COMPLETE

All 5 phases below are fully implemented. The editor's language server reports **no errors** in any modified file. A final `./mvnw.cmd clean install` has **not** been run yet (user skipped).

### Phase 1 — DB Migration + Enums + Exception

- **V13 migration** (`db/migration/V13__*.sql`): adds `monthly_commitment_amount NUMERIC(19,2) NOT NULL DEFAULT 0` and `arrears_balance NUMERIC(19,2) NOT NULL DEFAULT 0` to `project_members`.
- **Enum additions**:
  - `ProjectStatus`: added `FROZEN`, `ABANDONED` (kept legacy `CANCELLED` if present)
  - `JoinStatus`: existing `INVITED`, `JOINED` (kicked members are removed, not status-changed)
  - `Category`: added `PROJECT_REFUND` for income-side ledger reversal
- **New exception**: `domain/exceptions/InsufficientSafeSpendingException(BigDecimal requested, BigDecimal available)`

### Phase 2 — Domain Layer Extensions

**`ProjectMember`** — extended:
- New fields: `monthlyCommitmentAmount: BigDecimal`, `arrearsBalance: BigDecimal`
- New 5-arg constructor (with both new fields, null-safe → `BigDecimal.ZERO`)
- Methods:
  - `setCommitment(BigDecimal newCommitment)`
  - `accrueArrears(BigDecimal shortfall)` — adds to `arrearsBalance` if `shortfall > 0`
  - `isOver3MonthDefault()` — returns `arrearsBalance >= monthlyCommitmentAmount × 3 && commitment > 0`
  - `isAdmin()` — existing
  - Getters for both new fields

**`Project`** — extended:
- Lifecycle methods:
  - `freeze(UserId actor)` — owner check unless `actor == null` (system path). Sets status `FROZEN`. Idempotent if already FROZEN.
  - `unfreeze(UserId actor)` — owner only, requires `sumActiveArrears() == 0`, restores to `ACTIVE`.
  - `abandon(UserId actor)` — owner only, sets status `ABANDONED`.
  - `kickForDefault(UserId memberId)` — removes a JOINED member when in default (used by worker for group projects).
  - `registerOwnerAsMember(BigDecimal commitment)` — used in CreateProjectUseCase to seed owner as admin member.
  - `sumActiveArrears(): BigDecimal` — sums `arrearsBalance` across all JOINED members.
  - `acceptInvite(UserId invitee)` — existing (line 231)
- `requireContributable()` — domain guard: throws if status is `FROZEN`/`ABANDONED`/`COMPLETED`.

**`SafeSpendingService`** — extended:
- `requireCanAfford(UserId userId, BigDecimal additionalMonthlyOutflow)` — throws `InsufficientSafeSpendingException` if would push safe spending negative.
- `recalculateAndSaveSafeSpending(UserId userId)` — existing.

### Phase 3a — Modified Existing Use Cases

| Use Case | Change |
|---|---|
| `CreateProjectUseCase` | Calls `requireCanAfford` for owner monthly saving (personal) or commitment (group); calls `project.registerOwnerAsMember(commitment)` for GROUP. |
| `InviteProjectMemberUseCase` | Now requires `monthlyCommitmentAmount` (4-arg form); throws `MONTHLY_COMMITMENT_REQUIRED` if null/<= 0. |
| `AcceptProjectInviteUseCase` | On accept, calls `requireCanAfford` against the invited member's commitment before flipping JoinStatus → JOINED + recalcs safe spending. |
| `UpdateProjectUseCase` | Blocks any edit when status == `ABANDONED`; on `monthlySaving` raise, computes `delta` and calls `requireCanAfford`. |
| `UpdateProjectTrackingUseCase` | **Not modified** — domain `requireContributable()` covers status; no monthly raise occurs in this path. |
| `DeleteProjectUseCase` | **Repurposed** as Abandon: calls `p.abandon(owner)`, then per contribution creates `Transaction.create(contributorId, money, INCOME, PROJECT_REFUND, "Refund: project '<name>' abandoned", now())` with defensive try/catch + recalcs safe spending for owner + all contributors. |

### Phase 3b — New Use Cases

- **`FreezeProjectUseCase`** — owner-initiated `p.freeze(actor)` + save + return `ProjectDetailResponse`.
- **`UnfreezeProjectUseCase`** — `p.unfreeze(actor)` (domain enforces owner + zero arrears) + `safeSpendingService.recalculateAndSaveSafeSpending(actor)`.
- **`UpdateMemberCommitmentRequest`** (DTO) — `@NotNull @DecimalMin("0.01") BigDecimal amount`.
- **`UpdateMemberCommitmentUseCase`** — authorization: owner OR a JOINED admin member; blocks ABANDONED; if `delta > 0` calls `requireCanAfford(target, delta)`; calls `member.setCommitment(newCommitment)`; recalcs target's safe spending.
- **`AssessProjectArrearsUseCase`** (heart of the worker):
  - `execute()` — iterates `projectRepository.findAllByStatus(ProjectStatus.ACTIVE)`, dispatches GROUP vs PERSONAL, collects affected users for batch safe-spending recalc.
  - `processGroup(p, from, to)` — For each JOINED member with positive commitment: `shortfall = commitment - sumMemberContributions(p, m.userId, from, to)`; if `> 0` → `m.accrueArrears(shortfall)`. Then snapshot-iterates for `isOver3MonthDefault()`: if owner → `p.freeze(null)` + break; else → `p.kickForDefault(m.getUserId())`. Refresh tracking + `advanceMonth()`.
  - `processPersonal(p, from, to)` — Shortfall vs `tracking.monthlySaving` and owner contributions; accumulates `tracking.moneyOwed`; if `newOwed >= monthlySaving × 3` → `p.freeze(null)`.
  - Cycle: previous calendar month via `startOfLastMonth()` / `startOfThisMonth()` using `ZoneId.systemDefault()`.
  - `advanceMonth(tracking)`: `currentMonth + 1`, `monthLeft - 1` (floor 0).

### Phase 4 — Persistence

- **`ProjectMemberEntity`** — added two JPA columns:
  ```java
  @Column(name = "monthly_commitment_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal monthlyCommitmentAmount;

  @Column(name = "arrears_balance", nullable = false, precision = 19, scale = 2)
  private BigDecimal arrearsBalance;
  ```
- **`ProjectMemberMapper`** — rewrote:
  - `toDomain()` is now **hand-rolled** (uses the 5-arg `ProjectMember` constructor; null-safe defaults to `BigDecimal.ZERO`).
  - `toEntity()` still MapStruct-generated with extended `@AfterMapping afterMappingToEntity` that sets both new fields (null → ZERO).
  - **Risk if MapStruct complains**: change class from `abstract` to a regular class (since `toDomain` is no longer abstract).
- **`GroupDebtAssessmentWorker`** (NEW) at `infrastructure/worker/`:
  ```java
  @Component @RequiredArgsConstructor
  public class GroupDebtAssessmentWorker {
      private final AssessProjectArrearsUseCase assessProjectArrearsUseCase;
      @Scheduled(cron = "0 30 0 1 * *")
      public void run() { try { assessProjectArrearsUseCase.execute(); } catch (Exception e) { /* log */ } }
  }
  ```

### Phase 5 — Presentation

- **`ProjectMemberResponse`** (NEW DTO at `application/dtos/responses/project/`):
  - Fields: `userId, joinStatus, admin, monthlyCommitmentAmount, arrearsBalance`
  - Static `fromDomain(ProjectMember)`
- **`ProjectDetailResponse`** — added `private List<ProjectMemberResponse> members`; `fromDomain()` maps via `p.getMembers().stream().map(ProjectMemberResponse::fromDomain).toList()`.
- **`ProjectController`** — added 3 new use case injections + 3 new endpoints:
  - `POST /api/projects/{projectId}/freeze`
  - `POST /api/projects/{projectId}/unfreeze`
  - `PATCH /api/projects/{projectId}/members/{userId}/commitment` (body: `UpdateMemberCommitmentRequest`)
  - All return `CheckResponse<ProjectDetailResponse>`.
- **`GlobalExceptionHandler`** — added handler placed **above** `handleBusinessException`:
  ```java
  @ExceptionHandler(InsufficientSafeSpendingException.class)
  public ResponseEntity<...> handleInsufficientSafeSpending(...)
  // returns HTTP 422 with { requested, available, timestamp, path }
  ```

---

## 5. Known Risks / Things To Verify on Resume

1. **Run `./mvnw.cmd clean install`** — was skipped. Editor language server is clean but Maven hasn't cross-checked annotation processors yet.
2. **MapStruct generation** — verify `target/generated-sources/annotations/.../ProjectMemberMapperImpl.java` properly delegates to the hand-rolled `toDomain`. If broken, demote class from `abstract` to regular.
3. **`ProjectErrorCode.PROJECT_DUPLICATE_MEMBER`** — assumed to exist in `registerOwnerAsMember`. Verify or add.
4. **Callers of old 3-arg `Project.inviteMember(...)`** — anything still using it will now throw `MONTHLY_COMMITMENT_REQUIRED`. Check tests / seed data.
5. **Worker notifications** — when worker freezes/kicks, currently no event is published. Future work: integrate `WebSocketNotificationPublisher`.

---

## 6. Suggested Follow-Ups (not yet done)

- **Integration tests** for `POST /freeze`, `POST /unfreeze`, `PATCH /commitment`
- **Unit tests** for `AssessProjectArrearsUseCase` (group + personal, default kick, owner freeze)
- **Notification events** from worker
- **Swagger doc enrichment** for the 3 new endpoints

---

## 7. Key Files Touched (this work)

**Domain**
- `domain/models/project/Project.java` (lifecycle methods)
- `domain/models/project/ProjectMember.java` (commitment + arrears)
- `domain/services/SafeSpendingService.java` (`requireCanAfford`)
- `domain/exceptions/InsufficientSafeSpendingException.java` (NEW)

**Application — Use Cases**
- `application/usecases/project/CreateProjectUseCase.java`
- `application/usecases/project/InviteProjectMemberUseCase.java`
- `application/usecases/project/AcceptProjectInviteUseCase.java`
- `application/usecases/project/UpdateProjectUseCase.java`
- `application/usecases/project/DeleteProjectUseCase.java` (repurposed as abandon)
- `application/usecases/project/FreezeProjectUseCase.java` (NEW)
- `application/usecases/project/UnfreezeProjectUseCase.java` (NEW)
- `application/usecases/project/UpdateMemberCommitmentUseCase.java` (NEW)
- `application/usecases/project/AssessProjectArrearsUseCase.java` (NEW)

**Application — DTOs**
- `application/dtos/requests/project/UpdateMemberCommitmentRequest.java` (NEW)
- `application/dtos/responses/project/ProjectMemberResponse.java` (NEW)
- `application/dtos/responses/project/ProjectDetailResponse.java` (added members)

**Infrastructure**
- `infrastructure/persistence/entities/ProjectMemberEntity.java` (2 new columns)
- `infrastructure/persistence/mappers/ProjectMemberMapper.java` (rewritten)
- `infrastructure/worker/GroupDebtAssessmentWorker.java` (NEW)

**Presentation**
- `presentation/controllers/ProjectController.java` (3 new endpoints)
- `presentation/exception/GlobalExceptionHandler.java` (InsufficientSafeSpending handler)

**Database**
- `src/main/resources/db/migration/V13__*.sql` (NEW)

---

## 8. Next Step On Resume

> **Run** `.\mvnw.cmd clean install` **in the project root.**
> If it fails, the most likely culprit is the MapStruct `ProjectMemberMapper` — inspect the generated impl under `target/generated-sources/annotations/`. Otherwise iterate on any compile errors.
