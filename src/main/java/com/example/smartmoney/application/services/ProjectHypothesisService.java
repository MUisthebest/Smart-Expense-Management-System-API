package com.example.smartmoney.application.services;

import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.services.EnvelopeCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PROJECT_HYPOTHESIS — read-only preview math for structural project changes (create / shorten /
 * extend / delete a savings project), as opposed to {@link ProjectUpdateSuggestionService}'s
 * contribution/debt-amount adjustments to an existing project's tracking.
 *
 * <p>Shorten/extend reuses the exact {@code newMonthlySaving = remaining / newMonthLeft}
 * (ceiling) formula that {@code UpdateProjectUseCase.recalibrateTracking()} applies for real on
 * confirm — this service only previews it.
 *
 * <p>The {@code availableBudgetBefore/After} figures on the returned suggestion are the true
 * post-project-commitment ENVELOPE (safeSpending − targetSavings), delegated to {@link
 * EnvelopeCalculator} so the trait-based saving pool ({@code targetSavings}) is subtracted
 * correctly. Before this delegation, this service used {@code User.calculateSafeSpending}
 * directly, which subtracted commitment but not targetSavings — producing what-if answers that
 * were too pessimistic for AGGRESSIVE users on shorten scenarios and too optimistic on delete
 * scenarios (see EnvelopeCalculator javadoc for the specific numbers).
 *
 * <p>Purely read-only — never writes any record. {@code ConfirmChatActionUseCase} performs the
 * real mutation only after the user explicitly confirms.
 */
@Service
@RequiredArgsConstructor
public class ProjectHypothesisService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final EnvelopeCalculator envelopeCalculator;

    private static final Pattern SHORTEN_PATTERN = Pattern.compile(
            "rút ngắn|sớm hơn|hoàn thành sớm|kết thúc sớm|shorten|finish earlier|earlier",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern EXTEND_PATTERN = Pattern.compile(
            "kéo dài|gia hạn|dời|hoãn|trễ hơn|extend|push.*deadline|later",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern MONTHS_PATTERN = Pattern.compile(
            "(\\d+)\\s*(tháng|month|months)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "([\\d][\\d,.]*)\\s*(tỷ|ty|billion|triệu|tr|M|m|K|k|nghìn|ngàn)?",
            Pattern.UNICODE_CASE);
    private static final Pattern CREATE_PATTERN = Pattern.compile(
            "tạo (dự án|mục tiêu)|mở dự án|lập dự án|create (a )?(new )?project|start (a )?(new )?project|" +
                    "new project|new goal",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    // Up to 3 words allowed between the verb and "dự án"/"project" so a plural/count phrase like
    // "xóa các dự án", "xóa 2 dự án đã hoàn thành", "delete the completed projects" still matches
    // — not just the exact adjacent "xóa dự án" bigram.
    private static final Pattern DELETE_PATTERN = Pattern.compile(
            "xóa(?:\\s+\\S+){0,3}\\s+dự án|xoá(?:\\s+\\S+){0,3}\\s+dự án|" +
                    "hủy(?:\\s+\\S+){0,3}\\s+dự án|huỷ(?:\\s+\\S+){0,3}\\s+dự án|" +
                    "delete(?:\\s+\\S+){0,3}\\s+project|remove(?:\\s+\\S+){0,3}\\s+project|" +
                    "cancel(?:\\s+\\S+){0,3}\\s+project",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    // Status-category words for a bulk "delete all my completed/abandoned/... projects" request
    // (as opposed to one project referenced by name) — see previewDeleteByStatus.
    private static final Pattern COMPLETED_STATUS_PATTERN = Pattern.compile(
            "hoàn thành|hoàn tất|đã xong|completed|finished|\\bdone\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ABANDONED_STATUS_PATTERN = Pattern.compile(
            "đã hủy|đã huỷ|bị hủy|bị huỷ|hủy bỏ|huỷ bỏ|abandoned|cancelled|canceled",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern FROZEN_STATUS_PATTERN = Pattern.compile(
            "tạm dừng|đóng băng|frozen|paused",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern EXPIRED_STATUS_PATTERN = Pattern.compile(
            "hết hạn|quá hạn|expired",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern CREATE_NAME_PATTERN = Pattern.compile(
            "dự án(?: mới)?(?: tên)?\\s+([\\p{L}0-9 ]{2,40})", Pattern.UNICODE_CASE);
    // Clause separator for multi-project messages ("rút ngắn X 3 tháng VÀ kéo dài Y 2 tháng") —
    // bounds each project's keyword/duration window to its own clause so a number or direction
    // keyword meant for one project can't bleed into a neighboring project's window.
    private static final Pattern CLAUSE_SEPARATOR = Pattern.compile(
            "\\bvà\\b|\\band\\b|[,;]", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * True when the message clearly asks for a structural project change (shorten/extend/create/
     * delete) even if a full preview couldn't be computed (e.g. no duration parsed, or no
     * matching project). Callers use this to suppress unrelated proactive nudges — e.g.
     * {@code ProjectUpdateSuggestionService.checkFeasibility}'s "extend this project" nudge would
     * directly contradict an explicit "shorten this project" request if both fired on the same
     * turn.
     */
    public boolean looksLikeStructuralChangeRequest(String messageLow) {
        return CREATE_PATTERN.matcher(messageLow).find()
                || DELETE_PATTERN.matcher(messageLow).find()
                || SHORTEN_PATTERN.matcher(messageLow).find()
                || EXTEND_PATTERN.matcher(messageLow).find();
    }

    // ── Single-project shorten/extend ────────────────────────────────────────

    public Optional<ChatResponse.ProjectChangeSuggestion> previewShortenOrExtend(UUID userId, String messageLow) {
        List<Project> projects = activeProjects(userId);
        Project target = pickTargetProject(projects, messageLow);
        if (target == null || target.getTracking() == null) return Optional.empty();

        Optional<Integer> months = parseMonths(messageLow);
        if (months.isEmpty()) return Optional.empty();

        boolean shorten = SHORTEN_PATTERN.matcher(messageLow).find();
        boolean extend = EXTEND_PATTERN.matcher(messageLow).find();
        // Both matching is genuinely ambiguous (e.g. a message mentioning two different projects'
        // directions) — better to say nothing than to silently guess and possibly propose the
        // opposite of what the user asked.
        if (shorten == extend) return Optional.empty();

        LocalDate newDeadline = shorten
                ? target.getDeadline().minusMonths(months.get())
                : target.getDeadline().plusMonths(months.get());
        if (!newDeadline.isAfter(LocalDate.now())) return Optional.empty();

        return Optional.of(buildDeadlineChangeSuggestion(userId, projects, target, newDeadline,
                shorten ? ChatResponse.ProjectChangeOperation.SHORTEN : ChatResponse.ProjectChangeOperation.EXTEND,
                MessageLanguageDetector.isEnglish(messageLow)));
    }

    // ── Create ────────────────────────────────────────────────────────────────

    public Optional<ChatResponse.ProjectChangeSuggestion> previewCreate(UUID userId, String messageLow) {
        if (!CREATE_PATTERN.matcher(messageLow).find()) return Optional.empty();

        BigDecimal amount = parseAmount(messageLow);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();
        Optional<Integer> months = parseMonths(messageLow);
        if (months.isEmpty()) return Optional.empty();

        Matcher nameMatcher = CREATE_NAME_PATTERN.matcher(messageLow);
        String name = nameMatcher.find() ? capitalize(nameMatcher.group(1).trim()) : "Dự án mới";

        LocalDate newDeadline = LocalDate.now().plusMonths(months.get());
        int newMonthLeft = monthsToDeadline(LocalDate.now(), newDeadline);
        BigDecimal newMonthlySaving = amount.divide(BigDecimal.valueOf(newMonthLeft), 2, RoundingMode.CEILING);

        boolean english = MessageLanguageDetector.isEnglish(messageLow);
        BigDecimal commitmentBefore = totalCommitment(activeProjects(userId));
        BigDecimal commitmentAfter = commitmentBefore.add(newMonthlySaving);

        User user = userRepository.findById(userId).orElse(null);
        Long before = envelopeOrNull(user, commitmentBefore);
        Long after = envelopeOrNull(user, commitmentAfter);

        return Optional.of(ChatResponse.ProjectChangeSuggestion.builder()
                .projectId(null)
                .projectName(name)
                .operation(ChatResponse.ProjectChangeOperation.CREATE)
                .targetAmount(amount.longValue())
                .currentDeadline(null)
                .newDeadline(newDeadline)
                .currentMonthlySaving(null)
                .newMonthlySaving(newMonthlySaving.longValue())
                .availableBudgetBefore(before)
                .availableBudgetAfter(after)
                .reason(english
                        ? String.format(
                        "Create '%s': target %,.0f VND in %d months -> %,.0f VND/month. This decreases " +
                        "your available budget — spending will be tighter.",
                        name, amount, newMonthLeft, newMonthlySaving)
                        : String.format(
                        "Tạo dự án '%s': mục tiêu %,.0f VND trong %d tháng → %,.0f VND/tháng. Khoản này " +
                        "sẽ làm giảm ngân sách khả dụng — chi tiêu sẽ eo hẹp hơn.",
                        name, amount, newMonthLeft, newMonthlySaving))
                .build());
    }

    /**
     * BUG: {@code CreateProjectUseCase.validateCreate()} rejects a new ACTIVE project whose
     * priority collides with an existing ACTIVE project's priority ("each ACTIVE project must
     * have a unique priority"). The chat preview never surfaced or picked a priority at all, so
     * {@code ConfirmChatActionUseCase} always defaulted a chat-created project to MEDIUM — which
     * silently fails confirm with {@code PROJECT_ACTIVE_PRIORITY_CONFLICT} for any user who
     * already has an ACTIVE MEDIUM-priority project (a very common case, not an edge case).
     * Picks the lowest-ordinal priority not already used by one of the user's ACTIVE projects,
     * mirroring what a user filling the create form themselves would naturally do. Falls back to
     * MEDIUM only when all three are already taken — in that case {@code CreateProjectUseCase}
     * will already have rejected the request earlier via the separate "max 3 ACTIVE projects"
     * check, so this fallback is unreachable in practice, not a silent wrong guess.
     */
    public ProjectPriority pickAvailablePriorityForNewProject(UUID userId) {
        var usedByActive = projectRepository.findAllByUser(UserId.of(userId.toString())).stream()
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE)
                .map(Project::getPriority)
                .collect(java.util.stream.Collectors.toSet());
        return java.util.Arrays.stream(ProjectPriority.values())
                .filter(pr -> !usedByActive.contains(pr))
                .findFirst()
                .orElse(ProjectPriority.MEDIUM);
    }

    // ── Delete (single or bulk) ────────────────────────────────────────────────
    // Delete has different semantics from shorten/extend: unlike contribute/shorten/extend
    // (which need an ongoing project to make sense), a COMPLETED or ABANDONED
    // project can still be legitimately deleted by the user. So delete searches ALL of the
    // user's projects (deletableProjects), not just activeProjects.

    public Optional<ChatResponse.ProjectChangeSuggestion> previewDelete(UUID userId, String messageLow) {
        if (!DELETE_PATTERN.matcher(messageLow).find()) return Optional.empty();

        List<Project> projects = deletableProjects(userId);
        Project target = pickTargetProject(projects, messageLow);
        if (target == null) return Optional.empty();

        boolean english = MessageLanguageDetector.isEnglish(messageLow);
        User user = userRepository.findById(userId).orElse(null);
        Long current = envelopeOrNull(user, totalCommitment(activeProjects(userId)));

        return Optional.of(buildDeleteSuggestion(target, current, english));
    }

    /**
     * Deletes ALL of the user's projects matching a stated status category — e.g. "xóa các dự án
     * đã hoàn thành", "delete my completed projects" — rather than one project referenced by
     * name. Only fires when both a delete verb AND a recognizable status word are present;
     * otherwise returns empty and the caller falls through to the single-project {@link
     * #previewDelete}.
     */
    public List<ChatResponse.ProjectChangeSuggestion> previewDeleteByStatus(UUID userId, String messageLow) {
        if (!DELETE_PATTERN.matcher(messageLow).find()) return Collections.emptyList();

        ProjectStatus status = matchStatusWord(messageLow);
        if (status == null) return Collections.emptyList();

        List<Project> matches = projectRepository.findAllByUser(UserId.of(userId.toString())).stream()
                .filter(p -> p.getStatus() == status)
                .sorted(Comparator.comparingInt(p -> -p.getPriority().ordinal()))
                .toList();
        if (matches.isEmpty()) return Collections.emptyList();

        boolean english = MessageLanguageDetector.isEnglish(messageLow);
        User user = userRepository.findById(userId).orElse(null);
        Long current = envelopeOrNull(user, totalCommitment(activeProjects(userId)));

        return matches.stream()
                .map(p -> buildDeleteSuggestion(p, current, english))
                .toList();
    }

    private ProjectStatus matchStatusWord(String messageLow) {
        if (COMPLETED_STATUS_PATTERN.matcher(messageLow).find()) return ProjectStatus.COMPLETED;
        if (ABANDONED_STATUS_PATTERN.matcher(messageLow).find()) return ProjectStatus.ABANDONED;
        if (FROZEN_STATUS_PATTERN.matcher(messageLow).find()) return ProjectStatus.FROZEN;
        if (EXPIRED_STATUS_PATTERN.matcher(messageLow).find()) return ProjectStatus.EXPIRED;
        return null;
    }

    private ChatResponse.ProjectChangeSuggestion buildDeleteSuggestion(
            Project target, Long currentEnvelope, boolean english) {
        ProjectTracking tracking = target.getTracking();
        BigDecimal freed = tracking != null && tracking.getMonthlySaving() != null
                ? tracking.getMonthlySaving() : BigDecimal.ZERO;

        return ChatResponse.ProjectChangeSuggestion.builder()
                .projectId(target.getId().toString())
                .projectName(target.getName())
                .operation(ChatResponse.ProjectChangeOperation.DELETE)
                .targetAmount(target.getTargetAmount().amount().longValue())
                .currentDeadline(target.getDeadline())
                .newDeadline(null)
                .currentMonthlySaving(freed.longValue())
                .newMonthlySaving(null)
                // Budget stays unchanged on delete — the freed amount (if any — zero for a
                // project that's already COMPLETED/ABANDONED) is a surplus the user redirects
                // manually, not an automatic budget-category increase.
                .availableBudgetBefore(currentEnvelope)
                .availableBudgetAfter(currentEnvelope)
                .reason(english
                        ? String.format(
                        "Delete '%s' frees %,.0f VND/month. Your available budget stays the same — " +
                        "consider redirecting the freed amount to savings or another goal.",
                        target.getName(), freed)
                        : String.format(
                        "Xóa '%s' giải phóng %,.0f VND/tháng. Ngân sách khả dụng không đổi — cân nhắc " +
                        "chuyển khoản dư này sang tiết kiệm hoặc mục tiêu khác.",
                        target.getName(), freed))
                .build();
    }

    // ── Multi-project batch (Project A shortened + Project B extended, ...) ─────

    public List<ChatResponse.ProjectChangeSuggestion> previewMultiProject(UUID userId, String messageLow) {
        List<Project> projects = activeProjects(userId);

        record Mention(Project project, int start, int end) {}
        List<Mention> mentions = projects.stream()
                .map(p -> {
                    int idx = messageLow.indexOf(p.getName().toLowerCase());
                    return idx < 0 ? null : new Mention(p, idx, idx + p.getName().length());
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(Mention::start))
                .toList();
        if (mentions.size() < 2) return Collections.emptyList();

        List<Integer> clauseStarts = new ArrayList<>();
        List<Integer> clauseEnds = new ArrayList<>();
        Matcher clauseMatcher = CLAUSE_SEPARATOR.matcher(messageLow);
        while (clauseMatcher.find()) {
            clauseStarts.add(clauseMatcher.start());
            clauseEnds.add(clauseMatcher.end());
        }

        boolean english = MessageLanguageDetector.isEnglish(messageLow);
        BigDecimal commitmentBefore = totalCommitment(projects);
        BigDecimal commitmentAfter = commitmentBefore;

        record Change(Project project, LocalDate newDeadline,
                      ChatResponse.ProjectChangeOperation op, BigDecimal newMonthlySaving) {}
        List<Change> changes = new ArrayList<>();

        for (int i = 0; i < mentions.size(); i++) {
            Mention m = mentions.get(i);
            Project p = m.project();
            if (p.getTracking() == null) continue;

            // Bound the search window between the previous and next project mention (so a
            // direction/duration keyword meant for one project can't bleed into a neighbor's
            // window) and look both before and after the name — Vietnamese often phrases the
            // direction keyword BEFORE the name ("rút ngắn dự án X") rather than after.
            int prevEnd = i == 0 ? 0 : mentions.get(i - 1).end();
            int nextStart = i == mentions.size() - 1 ? messageLow.length() : mentions.get(i + 1).start();
            int windowStart = Math.max(prevEnd, m.start() - 30);
            int windowEnd = Math.min(nextStart, m.end() + 30);

            // Tighten to the nearest clause separator on each side, if one falls inside the
            // current bound — this is what actually splits "shorten A ... and extend B ..." so
            // A's window doesn't run forward into B's clause and vice versa.
            for (int j = clauseEnds.size() - 1; j >= 0; j--) {
                if (clauseEnds.get(j) <= m.start() && clauseEnds.get(j) >= windowStart) {
                    windowStart = clauseEnds.get(j);
                    break;
                }
            }
            for (int cs : clauseStarts) {
                if (cs >= m.end() && cs <= windowEnd) {
                    windowEnd = cs;
                    break;
                }
            }
            String window = messageLow.substring(windowStart, windowEnd);

            Optional<Integer> months = parseMonths(window);
            if (months.isEmpty()) continue;
            boolean shorten = SHORTEN_PATTERN.matcher(window).find();
            boolean extend = EXTEND_PATTERN.matcher(window).find();
            if (!shorten && !extend) continue;

            LocalDate newDeadline = shorten
                    ? p.getDeadline().minusMonths(months.get())
                    : p.getDeadline().plusMonths(months.get());
            if (!newDeadline.isAfter(LocalDate.now())) continue;

            int newMonthLeft = monthsToDeadline(LocalDate.now(), newDeadline);
            BigDecimal remaining = p.getTargetAmount().amount().subtract(p.getTracking().getMoneySaved());
            BigDecimal newMonthlySaving = remaining.compareTo(BigDecimal.ZERO) <= 0
                    ? BigDecimal.ZERO
                    : remaining.divide(BigDecimal.valueOf(newMonthLeft), 2, RoundingMode.CEILING);

            BigDecimal oldMonthlySaving = p.getTracking().getMonthlySaving() != null
                    ? p.getTracking().getMonthlySaving() : BigDecimal.ZERO;
            commitmentAfter = commitmentAfter.subtract(oldMonthlySaving).add(newMonthlySaving);

            changes.add(new Change(p, newDeadline,
                    shorten ? ChatResponse.ProjectChangeOperation.SHORTEN : ChatResponse.ProjectChangeOperation.EXTEND,
                    newMonthlySaving));
        }
        if (changes.isEmpty()) return Collections.emptyList();

        User user = userRepository.findById(userId).orElse(null);
        Long before = envelopeOrNull(user, commitmentBefore);
        Long after = envelopeOrNull(user, commitmentAfter);

        List<ChatResponse.ProjectChangeSuggestion> result = new ArrayList<>();
        for (Change c : changes) {
            BigDecimal currentSaving = c.project().getTracking().getMonthlySaving() != null
                    ? c.project().getTracking().getMonthlySaving() : BigDecimal.ZERO;
            result.add(ChatResponse.ProjectChangeSuggestion.builder()
                    .projectId(c.project().getId().toString())
                    .projectName(c.project().getName())
                    .operation(c.op())
                    .targetAmount(c.project().getTargetAmount().amount().longValue())
                    .currentDeadline(c.project().getDeadline())
                    .newDeadline(c.newDeadline())
                    .currentMonthlySaving(currentSaving.longValue())
                    .newMonthlySaving(c.newMonthlySaving().longValue())
                    .availableBudgetBefore(before)
                    .availableBudgetAfter(after)
                    .reason(english
                            ? String.format("'%s' -> new monthly saving %,.0f VND/month.",
                            c.project().getName(), c.newMonthlySaving())
                            : String.format("'%s' → mức tiết kiệm hàng tháng mới %,.0f VND/tháng.",
                            c.project().getName(), c.newMonthlySaving()))
                    .build());
        }
        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ChatResponse.ProjectChangeSuggestion buildDeadlineChangeSuggestion(
            UUID userId, List<Project> projects, Project target, LocalDate newDeadline,
            ChatResponse.ProjectChangeOperation operation, boolean english) {

        int newMonthLeft = monthsToDeadline(LocalDate.now(), newDeadline);
        BigDecimal remaining = target.getTargetAmount().amount().subtract(target.getTracking().getMoneySaved());
        BigDecimal newMonthlySaving = remaining.compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ZERO
                : remaining.divide(BigDecimal.valueOf(newMonthLeft), 2, RoundingMode.CEILING);

        BigDecimal oldMonthlySaving = target.getTracking().getMonthlySaving() != null
                ? target.getTracking().getMonthlySaving() : BigDecimal.ZERO;
        BigDecimal commitmentBefore = totalCommitment(projects);
        BigDecimal commitmentAfter = commitmentBefore.subtract(oldMonthlySaving).add(newMonthlySaving);

        User user = userRepository.findById(userId).orElse(null);
        Long before = envelopeOrNull(user, commitmentBefore);
        Long after = envelopeOrNull(user, commitmentAfter);

        boolean shorten = operation == ChatResponse.ProjectChangeOperation.SHORTEN;
        String reason = english
                ? String.format(
                "'%s' new deadline %s -> %,.0f VND/month (was %,.0f). %s",
                target.getName(), newDeadline, newMonthlySaving, oldMonthlySaving,
                shorten ? "Available budget decreases — spending will be tighter."
                : "Available budget stays stable or improves slightly — keep spending disciplined rather than increasing lifestyle expenses.")
                : String.format(
                "'%s' hạn mới %s → %,.0f VND/tháng (trước %,.0f). %s",
                target.getName(), newDeadline, newMonthlySaving, oldMonthlySaving,
                shorten ? "Ngân sách khả dụng sẽ giảm — chi tiêu sẽ eo hẹp hơn."
                : "Ngân sách khả dụng giữ ổn định hoặc nhỉnh hơn — nên duy trì kỷ luật chi tiêu thay vì tăng chi tiêu sinh hoạt.");

        return ChatResponse.ProjectChangeSuggestion.builder()
                .projectId(target.getId().toString())
                .projectName(target.getName())
                .operation(operation)
                .targetAmount(target.getTargetAmount().amount().longValue())
                .currentDeadline(target.getDeadline())
                .newDeadline(newDeadline)
                .currentMonthlySaving(oldMonthlySaving.longValue())
                .newMonthlySaving(newMonthlySaving.longValue())
                .availableBudgetBefore(before)
                .availableBudgetAfter(after)
                .reason(reason)
                .build();
    }

    /**
     * Returns the user's post-commitment envelope (safeSpending − targetSavings) for a given
     * hypothetical total commitment, or {@code null} if the user hasn't completed financial
     * setup or the calculator fails for any other reason.
     *
     * <p>Replaces the pre-EnvelopeCalculator implementation, which called
     * {@code user.calculateSafeSpending(commitment)} directly and returned just the safeSpending
     * half — never subtracting the trait-target savings. For BALANCED users with commitment ≥
     * savingCapacity, both formulas give the same number (targetSavings is 0). For AGGRESSIVE
     * users or users whose projects don't fully consume savingCapacity, the two diverge by up
     * to {@code savingCapacity − commitment} VND per month, which is what this fix removes.
     */
    private Long envelopeOrNull(User user, BigDecimal commitment) {
        if (user == null || !user.hasCompletedFinancialSetup()) return null;
        try {
            return envelopeCalculator
                    .computeFromCommitment(user, commitment, null)
                    .envelope()
                    .longValue();
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal totalCommitment(List<Project> projects) {
        return projects.stream()
                .filter(p -> p.getTracking() != null && p.getTracking().getMonthlySaving() != null)
                .map(p -> p.getTracking().getMonthlySaving())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Active projects (status ACTIVE — excludes COMPLETED/ABANDONED/FROZEN/EXPIRED/CANCELLED,
     *  future deadline, has tracking), highest priority first (HIGH &gt; MEDIUM &gt; LOW). */
    private List<Project> activeProjects(UUID userId) {
        LocalDate today = LocalDate.now();
        return projectRepository.findAllByUser(UserId.of(userId.toString())).stream()
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE
                        && p.getDeadline().isAfter(today) && p.getTracking() != null
                        && ChronoUnit.MONTHS.between(today, p.getDeadline()) > 0)
                .sorted(Comparator.comparingInt(p -> -p.getPriority().ordinal()))
                .toList();
    }

    /** ALL of the user's projects regardless of status, highest priority first — used only by
     *  the delete path, since deleting is a valid cleanup action even for a project that's
     *  already COMPLETED/ABANDONED/FROZEN/EXPIRED (unlike contribute/shorten/extend, which need
     *  an ongoing project and stay restricted to {@link #activeProjects}). */
    private List<Project> deletableProjects(UUID userId) {
        return projectRepository.findAllByUser(UserId.of(userId.toString())).stream()
                .sorted(Comparator.comparingInt(p -> -p.getPriority().ordinal()))
                .toList();
    }

    /** Match by project name mentioned in the message; else fall back to highest priority. */
    private Project pickTargetProject(List<Project> projects, String messageLow) {
        Optional<Project> named = projects.stream()
                .filter(p -> messageLow.contains(p.getName().toLowerCase()))
                .findFirst();
        if (named.isPresent()) return named.get();
        return projects.stream().findFirst().orElse(null);
    }

    private int monthsToDeadline(LocalDate today, LocalDate deadline) {
        int months = (deadline.getYear() - today.getYear()) * 12
                + (deadline.getMonthValue() - today.getMonthValue()) + 1;
        return Math.max(1, months);
    }

    private Optional<Integer> parseMonths(String text) {
        Matcher m = MONTHS_PATTERN.matcher(text);
        if (m.find()) {
            try {
                return Optional.of(Integer.parseInt(m.group(1)));
            } catch (NumberFormatException ignored) {}
        }
        return Optional.empty();
    }

    private String capitalize(String s) {
        if (s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public BigDecimal parseAmount(String text) {
        Matcher m = AMOUNT_PATTERN.matcher(text);
        while (m.find()) {
            String numStr = m.group(1).replace(",", "");
            String unit = m.group(2);
            try {
                BigDecimal num = new BigDecimal(numStr);
                if (unit != null) {
                    return switch (unit.toLowerCase()) {
                        case "tỷ", "ty", "billion" -> num.multiply(BigDecimal.valueOf(1_000_000_000));
                        case "triệu", "tr", "m" -> num.multiply(BigDecimal.valueOf(1_000_000));
                        case "k", "nghìn", "ngàn" -> num.multiply(BigDecimal.valueOf(1_000));
                        default -> num;
                    };
                }
                if (num.compareTo(BigDecimal.valueOf(1_000)) >= 0) return num;
            } catch (NumberFormatException ignored) {}
        }
        return BigDecimal.ZERO;
    }
}