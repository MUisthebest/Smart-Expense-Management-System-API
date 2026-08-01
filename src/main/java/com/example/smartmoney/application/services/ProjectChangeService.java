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
     * Validates a draft and builds its preview.
     *
     * @param draft      the assistant's accumulated draft; null/incomplete yields empty
     * @param messageLow the lowercased user message, used only to pick the reason's language
     * @return the preview, or empty when the draft isn't yet complete/legal enough to confirm
     */
    public Optional<Preview> preview(UUID userId, JsonSchemaGuardrail.ProjectDraft draft,
                                     String messageLow) {
        if (draft == null || !draft.complete()) return Optional.empty();

        ChatResponse.ProjectChangeOperation operation = parseOperation(draft.operation());
        if (operation == null) return Optional.empty();

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
            return Optional.empty();
        }
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    private Optional<Preview> previewCreate(UUID userId, JsonSchemaGuardrail.ProjectDraft draft,
                                            boolean english) {
        String name = trim(draft.name(), MAX_NAME_LENGTH);
        if (name == null) return Optional.empty();

        BigDecimal target = draft.targetAmount();
        if (target == null || target.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();

        LocalDate today = LocalDate.now(clock);
        LocalDate deadline = parseDeadline(draft.deadline());
        if (deadline == null || deadline.isBefore(today)) return Optional.empty();

        List<Project> live = liveProjects(userId);
        if (live.size() >= MAX_LIVE_PROJECTS) return Optional.empty();

        int monthsLeft = monthsToDeadline(today, deadline);
        if (monthsLeft > MAX_MONTHS_TO_DEADLINE) return Optional.empty();

        // A priority the user named is honoured only if it's actually free; otherwise fall back
        // to the same "pick an unused one" behaviour a user filling the form would land on. Both
        // paths can come back empty when all three are taken — but that only happens alongside
        // the live-project cap above, which already returned.
        ProjectPriority priority = resolvePriority(draft.priority(), live);
        if (priority == null) return Optional.empty();

        BigDecimal monthlySaving = target.divide(BigDecimal.valueOf(monthsLeft), 2, RoundingMode.CEILING);
        User user = userRepository.findById(userId).orElse(null);
        BigDecimal commitmentBefore = totalCommitment(live);
        BigDecimal commitmentAfter = commitmentBefore.add(monthlySaving);

        // Saving-capacity check mirrors CreateProjectUseCase's own. A user with no financial
        // setup has no capacity to compare against — that request would be rejected there too,
        // so it must not become a card either.
        if (user == null || !user.hasCompletedFinancialSetup()) return Optional.empty();
        BigDecimal capacity = user.getSavingCapacity();
        if (capacity != null && commitmentAfter.compareTo(capacity) > 0) return Optional.empty();

        String currency = draft.currency() == null ? "VND" : draft.currency();
        String description = trim(draft.description(), MAX_DESCRIPTION_LENGTH);

        var suggestion = ChatResponse.ProjectChangeSuggestion.builder()
                .projectId(null)
                .projectName(name)
                .operation(ChatResponse.ProjectChangeOperation.CREATE)
                .targetAmount(target.longValue())
                .newDeadline(deadline)
                .newMonthlySaving(monthlySaving.longValue())
                .availableBudgetBefore(safeSpendingOrNull(user, commitmentBefore))
                .availableBudgetAfter(safeSpendingOrNull(user, commitmentAfter))
                .reason(english
                        ? String.format("Create '%s': target %,d VND by %s (%d months) -> %,d VND/month.",
                        name, target.longValue(), DMY.format(deadline), monthsLeft,
                        monthlySaving.longValue())
                        : String.format("Tạo dự án '%s': mục tiêu %,d VND tới %s (%d tháng) -> %,d VND/tháng.",
                        name, target.longValue(), DMY.format(deadline), monthsLeft,
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
                .newDeadline(deadline)
                .build();

        return Optional.of(new Preview(suggestion, action));
    }

    // ── UPDATE (deadline / target / name / priority) ─────────────────────────

    private Optional<Preview> previewUpdate(UUID userId, JsonSchemaGuardrail.ProjectDraft draft,
                                            boolean english) {
        Project target = resolveOwnedProject(userId, draft.projectId(), draft.name());
        if (target == null || target.getTracking() == null) return Optional.empty();
        // A sub-personal project's terms are a shared commitment — §5 of the chatbot prompt keeps
        // those in the Group screen, and ConfirmChatActionUseCase rejects them as a last line of
        // defence. Never build a card that would only be thrown away.
        if (target.getGroupProjectId() != null) return Optional.empty();
        if (target.getStatus() != ProjectStatus.ACTIVE) return Optional.empty();

        LocalDate today = LocalDate.now(clock);
        LocalDate newDeadline = parseDeadline(draft.deadline());
        if (newDeadline != null && (newDeadline.isBefore(today)
                || monthsToDeadline(today, newDeadline) > MAX_MONTHS_TO_DEADLINE)) {
            return Optional.empty();
        }

        BigDecimal newTarget = draft.targetAmount();
        if (newTarget != null && newTarget.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();

        // An "update" that updates nothing is a draft the assistant marked complete too eagerly.
        if (newDeadline == null && newTarget == null) return Optional.empty();

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

        return Optional.of(new Preview(suggestion, action));
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    private Optional<Preview> previewDelete(UUID userId, JsonSchemaGuardrail.ProjectDraft draft,
                                            boolean english) {
        // Deleting is valid cleanup at any status, so this searches all of the user's projects
        // rather than just the live ones.
        Project target = resolveOwnedProject(userId, draft.projectId(), draft.name());
        if (target == null) return Optional.empty();
        if (target.getGroupProjectId() != null) return Optional.empty();

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

        return Optional.of(new Preview(suggestion, action));
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
