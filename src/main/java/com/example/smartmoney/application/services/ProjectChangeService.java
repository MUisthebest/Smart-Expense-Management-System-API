package com.example.smartmoney.application.services;

import com.example.smartmoney.application.dtos.chat.PendingChatAction;
import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.application.guardrails.JsonSchemaGuardrail;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.services.ProjectPacePlanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Turns the assistant's {@link JsonSchemaGuardrail.ProjectDraft} into a validated, confirmable
 * personal-project change — the project-side counterpart of {@code TransactionChangeService}.
 *
 * <p><b>Why this exists.</b> Project creation used to be reachable only through {@code
 * ProjectHypothesisService}, whose {@code previewCreate} is pure regex over ONE message: it needs
 * a create verb, a parseable amount, and a month count to all appear together. Anything
 * conversational — "tôi muốn tạo dự án mới" answered over three turns, or a target given as
 * "hai trăm triệu", or a deadline given as a date rather than a month count — produced no
 * suggestion, therefore no {@code pendingActionId}, therefore nothing for a later "có" to
 * confirm ({@code ResolvePlainAnswerUseCase} returns empty when there is no latest action). The
 * turn then fell through to the LLM, whose reply could easily read as though the project had
 * been created. Net effect: the user was told it worked and the project never appeared in
 * {@code GET /api/v1/projects}. Slot-filling by the model closes that gap the same way it
 * already does for transactions.
 *
 * <p><b>Why validation is duplicated here.</b> {@code CreateProjectUseCase} enforces the real
 * rules (financial setup complete, at most 3 live projects, one project per priority, deadline
 * within 60 months, total monthly commitment within saving capacity) by throwing. Throwing at
 * confirm time is far too late: the user has already said yes, and the message they get back is
 * a raw domain error. Re-checking the same rules here means a draft that would fail simply
 * doesn't become a card, so the assistant asks another question instead — and the checks that do
 * pass are re-run for real by the use case anyway, so this is a pre-flight, never the authority.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectChangeService {

    /** {@code CreateProjectUseCase}'s cap on simultaneously live projects. */
    private static final int MAX_LIVE_PROJECTS = 3;
    /** {@code CreateProjectUseCase}'s cap on how far out a deadline may sit. */
    private static final int MAX_MONTHS_TO_DEADLINE = 60;
    private static final int MAX_NAME_LENGTH = 120;
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    /** Both forms the model produces: "dd/MM/yyyy" when writing Vietnamese, ISO when English. */
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    /** A validated, ready-to-confirm project change: what to show, and what to run on "yes". */
    public record Preview(ChatResponse.ProjectChangeSuggestion suggestion,
                          PendingChatAction action) {}

    /**
     * The result of evaluating a draft: either a card, or the reason there isn't one.
     *
     * <p>The reason matters as much as the card. Every rejection used to be an anonymous
     * {@code Optional.empty()}, and the turn then returned the model's reply untouched — a reply
     * written on the assumption that a complete draft becomes a project. Users were told their
     * project was on its way and then found nothing in the project list, with no hint that the
     * request had hit the three-project cap or run past their saving capacity. A {@code note} is
     * attached for exactly those blockers, so the reply can say what went wrong and what to change.
     *
     * @param note user-facing explanation, already in the user's language; null when there is
     *             nothing to explain (a card was built, or the assistant is still collecting
     *             fields and its own question is the right thing to show)
     */
    public record Outcome(Preview preview, Rejection rejection, String note) {

        static Outcome of(Preview preview) {
            return new Outcome(preview, null, null);
        }

        /** Still gathering — the assistant's own follow-up question is the correct output. */
        static Outcome incomplete() {
            return new Outcome(null, Rejection.INCOMPLETE, null);
        }

        static Outcome blocked(Rejection rejection, String note) {
            return new Outcome(null, rejection, note);
        }

        public Optional<Preview> asOptional() {
            return Optional.ofNullable(preview);
        }
    }

    /** Why a draft did not become a confirmable card. */
    public enum Rejection {
        /** Fields are still missing, or the draft is malformed — not an error the user must act on. */
        INCOMPLETE,
        /** Projects need a completed financial setup; the create would be rejected downstream. */
        NO_FINANCIAL_SETUP,
        /** The user already holds the maximum number of simultaneous projects. */
        MAX_LIVE_PROJECTS,
        /** Existing commitments already consume the whole saving capacity. */
        NO_CAPACITY_LEFT,
        /** This particular plan costs more per month than the capacity that is left. */
        OVER_CAPACITY,
        /** Deadline sits beyond the horizon the backend accepts. */
        DEADLINE_TOO_FAR,
        /** Named project could not be resolved, or the name matched more than one. */
        PROJECT_NOT_FOUND,
        /** Target belongs to a group project — chat may not change it (§5). */
        GROUP_LINKED,
        /** Target is completed / expired / abandoned / frozen, so its terms can't be changed. */
        NOT_ACTIVE
    }

    /**
     * Validates a draft and builds its preview.
     *
     * @param draft      the assistant's accumulated draft; null/incomplete yields empty
     * @param messageLow the lowercased user message, used only to pick the reason's language
     * @return the preview, or empty when the draft isn't yet complete/legal enough to confirm
     */
    public Optional<Preview> preview(UUID userId, JsonSchemaGuardrail.ProjectDraft draft,
                                     String messageLow) {
        return evaluate(userId, draft, messageLow).asOptional();
    }

    /** {@link #preview} plus the reason, for callers that need to tell the user why nothing
     *  happened rather than leaving a false success claim standing. */
    public Outcome evaluate(UUID userId, JsonSchemaGuardrail.ProjectDraft draft, String messageLow) {
        if (draft == null || !draft.complete()) return Outcome.incomplete();

        ChatResponse.ProjectChangeOperation operation = parseOperation(draft.operation());
        if (operation == null) return Outcome.incomplete();

        boolean english = MessageLanguageDetector.isEnglish(messageLow);
        try {
            return switch (operation) {
                case CREATE -> previewCreate(userId, draft, english);
                case SHORTEN, EXTEND -> previewUpdate(userId, draft, english);
                case DELETE -> previewDelete(userId, draft, english);
            };
        } catch (Exception e) {
            // Any lookup/parse failure means "not confirmable yet", never a failed chat turn —
            // the reply itself is already in hand by the time this runs.
            log.warn("ProjectChangeService: could not preview {} for user {}: {}",
                    operation, userId, e.getMessage());
            return Outcome.incomplete();
        }
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    private Outcome previewCreate(UUID userId, JsonSchemaGuardrail.ProjectDraft draft,
                                  boolean english) {
        String name = trim(draft.name(), MAX_NAME_LENGTH);
        if (name == null) return Outcome.incomplete();

        BigDecimal target = draft.targetAmount();
        if (target == null || target.compareTo(BigDecimal.ZERO) <= 0) return Outcome.incomplete();

        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.hasCompletedFinancialSetup()) {
            return Outcome.blocked(Rejection.NO_FINANCIAL_SETUP, english
                    ? "Projects need your financial setup completed first — add your monthly income "
                    + "and saving pace in Settings, then we can create this one."
                    : "Cần hoàn tất thiết lập tài chính (thu nhập hàng tháng và nhịp độ tiết kiệm) "
                    + "trước khi tạo dự án — sau đó chúng ta tạo ngay.");
        }

        // The cap, the priority-uniqueness rule and the capacity check all count exactly what
        // CreateProjectUseCase counts: ACTIVE projects. Counting a different set here would either
        // block a create the backend would accept, or wave through one it is about to reject.
        List<Project> active = activeProjects(userId);
        if (active.size() >= MAX_LIVE_PROJECTS) {
            return Outcome.blocked(Rejection.MAX_LIVE_PROJECTS, english
                    ? String.format("You already have %d projects running, which is the maximum. "
                    + "Finish, cancel or delete one before adding another.", active.size())
                    : String.format("Bạn đang có %d dự án chạy song song — đã chạm giới hạn. Hãy hoàn tất, "
                    + "huỷ hoặc xoá bớt một dự án rồi tạo dự án mới.", active.size()));
        }

        ProjectPriority priority = resolvePriority(draft.priority(), active);
        if (priority == null) return Outcome.incomplete();

        BigDecimal capacity = user.getSavingCapacity() == null ? BigDecimal.ZERO : user.getSavingCapacity();
        BigDecimal commitmentBefore = plannedCommitment(active);
        BigDecimal capacityLeft = capacity.subtract(commitmentBefore);
        if (capacityLeft.signum() <= 0) {
            return Outcome.blocked(Rejection.NO_CAPACITY_LEFT, english
                    ? String.format("Your current projects already use your whole monthly saving capacity "
                    + "(%,d VND). Finish or shrink one before starting a new goal.", capacity.longValue())
                    : String.format("Các dự án hiện tại đã dùng hết khả năng tiết kiệm hàng tháng của bạn "
                    + "(%,d VND). Hãy hoàn tất hoặc giảm bớt một dự án trước khi mở mục tiêu mới.",
                    capacity.longValue()));
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate deadline = parseDeadline(draft.deadline());
        int months;
        BigDecimal monthlySaving;

        if (deadline != null && !deadline.isBefore(today)) {
            // The user named an actual date — honour it rather than re-planning around it.
            months = monthsToDeadline(today, deadline);
            if (months > MAX_MONTHS_TO_DEADLINE) {
                return Outcome.blocked(Rejection.DEADLINE_TOO_FAR, english
                        ? String.format("A project can run at most %d months; that deadline is %d months out. "
                        + "Pick a closer date.", MAX_MONTHS_TO_DEADLINE, months)
                        : String.format("Một dự án chỉ kéo dài tối đa %d tháng, còn thời hạn bạn nêu là %d tháng. "
                        + "Bạn chọn giúp mốc gần hơn nhé.", MAX_MONTHS_TO_DEADLINE, months));
            }
            monthlySaving = ProjectPacePlanner.monthlyCommitment(target, months);
        } else {
            // No date: plan it from the pace the user picked, exactly like the create screen does.
            ProjectPacePlanner.Mode mode = parsePace(draft.pace());
            if (mode == null) return Outcome.incomplete();
            ProjectPacePlanner.Plan plan = ProjectPacePlanner.plan(target, capacityLeft, mode);
            months = plan.months();
            monthlySaving = plan.monthlySaving();
            // months is inclusive of the current month, so an N-month plan ends N-1 months out.
            deadline = today.plusMonths(months - 1L);
        }

        BigDecimal commitmentAfter = commitmentBefore.add(monthlySaving);
        if (monthlySaving.compareTo(capacityLeft) > 0) {
            // Only reachable on the user-named-deadline branch: a planned horizon is derived from
            // capacityLeft and so can never exceed it. The suggestion names the horizon that would
            // fit, because "no" without a way forward is the least useful answer here.
            int feasibleMonths = ProjectPacePlanner.plan(
                    target, capacityLeft, ProjectPacePlanner.Mode.URGENT).months();
            return Outcome.blocked(Rejection.OVER_CAPACITY, english
                    ? String.format("That pace needs %,d VND/month but only %,d VND/month of your saving "
                    + "capacity is free. Stretching it to about %d months would fit — or lower the target.",
                    monthlySaving.longValue(), capacityLeft.longValue(), feasibleMonths)
                    : String.format("Kế hoạch này cần %,d VND/tháng, trong khi khả năng tiết kiệm còn trống "
                    + "chỉ %,d VND/tháng. Kéo dài khoảng %d tháng thì vừa — hoặc giảm số tiền mục tiêu.",
                    monthlySaving.longValue(), capacityLeft.longValue(), feasibleMonths));
        }

        String currency = draft.currency() == null ? "VND" : draft.currency();
        String description = trim(draft.description(), MAX_DESCRIPTION_LENGTH);

        final LocalDate finalDeadline = deadline;
        final int finalMonths = months;
        var suggestion = ChatResponse.ProjectChangeSuggestion.builder()
                .projectId(null)
                .projectName(name)
                .operation(ChatResponse.ProjectChangeOperation.CREATE)
                .targetAmount(target.longValue())
                .newDeadline(finalDeadline)
                .newMonthlySaving(monthlySaving.longValue())
                .availableBudgetBefore(safeSpendingOrNull(user, commitmentBefore))
                .availableBudgetAfter(safeSpendingOrNull(user, commitmentAfter))
                .reason(english
                        ? String.format("Create '%s': target %,d VND by %s (%d months) -> %,d VND/month.",
                        name, target.longValue(), DMY.format(finalDeadline), finalMonths,
                        monthlySaving.longValue())
                        : String.format("Tạo dự án '%s': mục tiêu %,d VND tới %s (%d tháng) -> %,d VND/tháng.",
                        name, target.longValue(), DMY.format(finalDeadline), finalMonths,
                        monthlySaving.longValue()))
                .build();

        var action = PendingChatAction.builder()
                .type(PendingChatAction.Type.PROJECT_CREATE)
                .name(name)
                .description(description)
                .projectType(ProjectType.PERSONAL.name())
                .priority(priority.name())
                .targetAmount(target)
                .currency(currency)
                .newDeadline(finalDeadline)
                .build();

        return Outcome.of(new Preview(suggestion, action));
    }

    private ProjectPacePlanner.Mode parsePace(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toUpperCase()) {
            case "URGENT" -> ProjectPacePlanner.Mode.URGENT;
            case "RELAXED" -> ProjectPacePlanner.Mode.RELAXED;
            default -> null;
        };
    }

    // ── UPDATE (deadline / target / name / priority) ─────────────────────────

    private Outcome previewUpdate(UUID userId, JsonSchemaGuardrail.ProjectDraft draft,
                                  boolean english) {
        Project target = resolveOwnedProject(userId, draft.projectId(), draft.name());
        if (target == null) {
            return Outcome.blocked(Rejection.PROJECT_NOT_FOUND, english
                    ? "I couldn't tell which project you mean — could you give me its exact name?"
                    : "Mình chưa xác định được bạn muốn sửa dự án nào — bạn cho mình tên chính xác của dự án nhé.");
        }
        if (target.getTracking() == null) return Outcome.incomplete();
        // A sub-personal project's terms are a shared commitment — §5 of the chatbot prompt keeps
        // those in the Group screen, and ConfirmChatActionUseCase rejects them as a last line of
        // defence. Never build a card that would only be thrown away.
        if (target.getGroupProjectId() != null) {
            return Outcome.blocked(Rejection.GROUP_LINKED, english
                    ? String.format("'%s' belongs to a group project, so its terms are changed from the "
                    + "Group screen rather than here.", target.getName())
                    : String.format("Dự án '%s' thuộc một dự án nhóm, nên phần điều khoản được sửa trong "
                    + "màn hình Nhóm chứ không sửa qua trò chuyện.", target.getName()));
        }
        if (target.getStatus() != ProjectStatus.ACTIVE) {
            return Outcome.blocked(Rejection.NOT_ACTIVE, english
                    ? String.format("'%s' is not running any more (%s), so its terms can't be changed. "
                    + "You can delete it or create a replacement.",
                    target.getName(), ProjectUpdateSuggestionService.statusPhrase(target.getStatus(), true))
                    : String.format("Dự án '%s' hiện không còn chạy (%s) nên không đổi được điều khoản. "
                    + "Bạn có thể xoá nó hoặc tạo một dự án mới thay thế.",
                    target.getName(), ProjectUpdateSuggestionService.statusPhrase(target.getStatus(), false)));
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate newDeadline = parseDeadline(draft.deadline());
        if (newDeadline != null && newDeadline.isBefore(today)) return Outcome.incomplete();
        if (newDeadline != null && monthsToDeadline(today, newDeadline) > MAX_MONTHS_TO_DEADLINE) {
            return Outcome.blocked(Rejection.DEADLINE_TOO_FAR, english
                    ? String.format("A project can run at most %d months, so that deadline is too far out.",
                    MAX_MONTHS_TO_DEADLINE)
                    : String.format("Một dự án chỉ kéo dài tối đa %d tháng nên mốc thời hạn đó xa quá.",
                    MAX_MONTHS_TO_DEADLINE));
        }

        BigDecimal newTarget = draft.targetAmount();
        if (newTarget != null && newTarget.compareTo(BigDecimal.ZERO) <= 0) return Outcome.incomplete();

        // An "update" that updates nothing is a draft the assistant marked complete too eagerly.
        if (newDeadline == null && newTarget == null) return Outcome.incomplete();

        LocalDate effectiveDeadline = newDeadline != null ? newDeadline : target.getDeadline();
        BigDecimal effectiveTarget = newTarget != null ? newTarget : target.getTargetAmount().amount();
        int monthsLeft = monthsToDeadline(today, effectiveDeadline);

        BigDecimal saved = target.getTracking().getMoneySaved() == null
                ? BigDecimal.ZERO : target.getTracking().getMoneySaved();
        BigDecimal remaining = effectiveTarget.subtract(saved).max(BigDecimal.ZERO);
        BigDecimal newMonthlySaving = remaining.divide(BigDecimal.valueOf(monthsLeft), 2, RoundingMode.CEILING);

        User user = userRepository.findById(userId).orElse(null);
        List<Project> live = liveProjects(userId);
        BigDecimal commitmentBefore = totalCommitment(live);
        BigDecimal commitmentAfter = commitmentBefore
                .subtract(monthlySavingOf(target))
                .add(newMonthlySaving);

        boolean shortening = effectiveDeadline.isBefore(target.getDeadline());
        var operation = shortening
                ? ChatResponse.ProjectChangeOperation.SHORTEN
                : ChatResponse.ProjectChangeOperation.EXTEND;

        var suggestion = ChatResponse.ProjectChangeSuggestion.builder()
                .projectId(target.getId().toString())
                .projectName(target.getName())
                .operation(operation)
                .targetAmount(effectiveTarget.longValue())
                .currentDeadline(target.getDeadline())
                .newDeadline(effectiveDeadline)
                .currentMonthlySaving(monthlySavingOf(target).longValue())
                .newMonthlySaving(newMonthlySaving.longValue())
                .availableBudgetBefore(safeSpendingOrNull(user, commitmentBefore))
                .availableBudgetAfter(safeSpendingOrNull(user, commitmentAfter))
                .reason(english
                        ? String.format("Update '%s': target %,d VND by %s -> %,d VND/month.",
                        target.getName(), effectiveTarget.longValue(),
                        DMY.format(effectiveDeadline), newMonthlySaving.longValue())
                        : String.format("Cập nhật dự án '%s': mục tiêu %,d VND tới %s -> %,d VND/tháng.",
                        target.getName(), effectiveTarget.longValue(),
                        DMY.format(effectiveDeadline), newMonthlySaving.longValue()))
                .build();

        var action = PendingChatAction.builder()
                .type(PendingChatAction.Type.PROJECT_UPDATE_DETAILS)
                .projectId(target.getId().toString())
                .newDeadline(newDeadline)
                .targetAmount(newTarget)
                .priority(parsePriority(draft.priority()) == null ? null : draft.priority().toUpperCase())
                .build();

        return Outcome.of(new Preview(suggestion, action));
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    private Outcome previewDelete(UUID userId, JsonSchemaGuardrail.ProjectDraft draft,
                                  boolean english) {
        // Deleting is valid cleanup at any status, so this searches all of the user's projects
        // rather than just the live ones.
        Project target = resolveOwnedProject(userId, draft.projectId(), draft.name());
        if (target == null) {
            return Outcome.blocked(Rejection.PROJECT_NOT_FOUND, english
                    ? "I couldn't tell which project you mean — could you give me its exact name?"
                    : "Mình chưa xác định được bạn muốn xoá dự án nào — bạn cho mình tên chính xác của dự án nhé.");
        }
        if (target.getGroupProjectId() != null) {
            return Outcome.blocked(Rejection.GROUP_LINKED, english
                    ? String.format("'%s' belongs to a group project — it is removed from the Group screen, "
                    + "not from here.", target.getName())
                    : String.format("Dự án '%s' thuộc một dự án nhóm — phần này được xử lý trong màn hình "
                    + "Nhóm chứ không xoá qua trò chuyện.", target.getName()));
        }

        var suggestion = ChatResponse.ProjectChangeSuggestion.builder()
                .projectId(target.getId().toString())
                .projectName(target.getName())
                .operation(ChatResponse.ProjectChangeOperation.DELETE)
                .currentDeadline(target.getDeadline())
                .reason(english
                        ? String.format("Delete '%s' permanently, including its contribution history.",
                        target.getName())
                        : String.format("Xóa vĩnh viễn dự án '%s', kể cả lịch sử đóng góp.",
                        target.getName()))
                .build();

        var action = PendingChatAction.builder()
                .type(PendingChatAction.Type.PROJECT_DELETE)
                .projectId(target.getId().toString())
                .name(target.getName())
                .build();

        return Outcome.of(new Preview(suggestion, action));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Resolves the draft's target project by id first, then by an exact-ish name match. The id is
     * always preferred: a name match is only a convenience for when the model echoes what the
     * user said instead of the id from the context block. Ownership is implicit — the lookup is
     * scoped to this user's own projects, so an id belonging to someone else simply won't resolve.
     */
    private Project resolveOwnedProject(UUID userId, String projectId, String name) {
        List<Project> mine = projectRepository.findAllByUser(UserId.of(userId.toString()));
        if (projectId != null) {
            Optional<Project> byId = mine.stream()
                    .filter(p -> p.getId().toString().equals(projectId))
                    .findFirst();
            if (byId.isPresent()) return byId.get();
        }
        if (name == null) return null;
        String needle = name.trim().toLowerCase();
        List<Project> byName = mine.stream()
                .filter(p -> p.getName().toLowerCase().equals(needle))
                .toList();
        // Two projects with the same name is ambiguous — the assistant must ask which one rather
        // than have this pick for the user.
        return byName.size() == 1 ? byName.get(0) : null;
    }

    /**
     * The set {@code CreateProjectUseCase.validateCreate} counts: ACTIVE projects only. The
     * three-project cap, the one-project-per-priority rule and the saving-capacity check are all
     * scoped to it there, so scoping them differently here would either block a create the backend
     * would happily accept or explain the block with the wrong reason.
     */
    private List<Project> activeProjects(UUID userId) {
        return projectRepository.findAllByUser(UserId.of(userId.toString())).stream()
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE)
                .sorted(Comparator.comparingInt(p -> -p.getPriority().ordinal()))
                .toList();
    }

    /**
     * Monthly commitment of a set of projects computed the way {@code CreateProjectUseCase} and
     * the budget advisor compute it — target spread over the months to the deadline, rather than
     * the tracking's {@code monthlySaving} (which is the remaining amount over the remaining
     * months, a different figure once contributions land). The capacity check has to use the same
     * arithmetic the backend will use, or a preview passes and the create throws.
     */
    private BigDecimal plannedCommitment(List<Project> projects) {
        LocalDate today = LocalDate.now(clock);
        return projects.stream()
                .map(p -> ProjectPacePlanner.monthlyCommitment(
                        p.getTargetAmount().amount(), monthsToDeadline(today, p.getDeadline())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Projects that count toward the 3-project cap and the priority-uniqueness rule — the same
     *  set {@code CreateProjectUseCase} counts (a FROZEN project still holds its slot). */
    private List<Project> liveProjects(UUID userId) {
        return projectRepository.findAllByUser(UserId.of(userId.toString())).stream()
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE || p.getStatus() == ProjectStatus.FROZEN)
                .sorted(Comparator.comparingInt(p -> -p.getPriority().ordinal()))
                .toList();
    }

    private ProjectPriority resolvePriority(String requested, List<Project> live) {
        Set<ProjectPriority> taken = live.stream()
                .map(Project::getPriority)
                .collect(Collectors.toSet());
        ProjectPriority asked = parsePriority(requested);
        if (asked != null && !taken.contains(asked)) return asked;
        return Arrays.stream(ProjectPriority.values())
                .filter(p -> !taken.contains(p))
                .findFirst()
                .orElse(null);
    }

    private ProjectPriority parsePriority(String raw) {
        if (raw == null) return null;
        try {
            return ProjectPriority.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ChatResponse.ProjectChangeOperation parseOperation(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toUpperCase()) {
            case "CREATE" -> ChatResponse.ProjectChangeOperation.CREATE;
            // The draft speaks in terms of "change this project's terms"; direction is derived
            // from the dates in previewUpdate rather than trusted from the model's label.
            case "UPDATE", "SHORTEN", "EXTEND" -> ChatResponse.ProjectChangeOperation.SHORTEN;
            case "DELETE" -> ChatResponse.ProjectChangeOperation.DELETE;
            default -> null;
        };
    }

    /** Accepts both "dd/MM/yyyy" and ISO "yyyy-MM-dd"; anything else is a hard failure, since
     *  guessing at a deadline the user did state would set the wrong saving pace. */
    private LocalDate parseDeadline(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        try {
            return LocalDate.parse(value, DMY);
        } catch (Exception ignored) {
            // fall through to ISO
        }
        try {
            return LocalDate.parse(value, ISO);
        } catch (Exception e) {
            return null;
        }
    }

    /** Inclusive of the current month, matching {@code CreateProjectUseCase}/
     *  {@code UpdateProjectUseCase} — "12 months from today" computes as 13 there too, and a
     *  different rounding here would show the user a monthly figure the backend won't reproduce. */
    private int monthsToDeadline(LocalDate today, LocalDate deadline) {
        int months = (deadline.getYear() - today.getYear()) * 12
                + (deadline.getMonthValue() - today.getMonthValue()) + 1;
        return Math.max(1, months);
    }

    private BigDecimal monthlySavingOf(Project project) {
        if (project.getTracking() == null || project.getTracking().getMonthlySaving() == null) {
            return BigDecimal.ZERO;
        }
        return project.getTracking().getMonthlySaving();
    }

    private BigDecimal totalCommitment(List<Project> projects) {
        return projects.stream()
                .map(this::monthlySavingOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Long safeSpendingOrNull(User user, BigDecimal commitment) {
        if (user == null || !user.hasCompletedFinancialSetup()) return null;
        try {
            return user.calculateSafeSpending(commitment).longValue();
        } catch (Exception e) {
            return null;
        }
    }

    private String trim(String raw, int max) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty()) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
