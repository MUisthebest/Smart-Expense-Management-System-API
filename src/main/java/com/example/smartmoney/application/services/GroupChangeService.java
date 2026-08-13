package com.example.smartmoney.application.services;

import com.example.smartmoney.application.dtos.chat.PendingChatAction;
import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.application.guardrails.JsonSchemaGuardrail;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.GroupProject;
import com.example.smartmoney.domain.models.group.enums.GroupProjectStatus;
import com.example.smartmoney.domain.models.group.enums.GroupStatus;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.GroupRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns the assistant's {@link JsonSchemaGuardrail.GroupDraft} into a validated, confirmable
 * group / group-project operation — the group-side counterpart of {@code TransactionChangeService}
 * and {@link ProjectChangeService}.
 *
 * <p><b>Why this exists.</b> Group flows previously had no {@code PendingChatAction.Type} at all,
 * so the assistant's only possible answer to "tạo dự án nhóm giúp tôi" was a walkthrough of the
 * screens to tap — accurate, but not what was asked for. Every operation below maps onto the same
 * use case the REST controller calls, so confirming in chat and tapping in the app now produce
 * identical results, including the invite email that {@code InviteGroupMemberUseCase} sends.
 *
 * <p><b>Why the permission checks are duplicated here.</b> The use cases enforce admin-only,
 * LOCKED-before-project, and one-project-per-group by throwing. At confirm time that surfaces as
 * a raw domain error after the user has already agreed. Checking first means an operation the
 * user can't perform never becomes a card, so the assistant explains the actual blocker instead —
 * the same pre-flight relationship {@link ProjectChangeService} has with {@code
 * CreateProjectUseCase}. The use cases re-check everything regardless; this is never the authority.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupChangeService {

    private static final int MAX_NAME_LENGTH = 255;
    private static final int MIN_TOTAL_MONTHS = 1;
    private static final int MAX_TOTAL_MONTHS = 60;
    /** {@code CreateProjectUseCase}'s live-project cap also gates joining a group project, since
     *  joining opens a sub-personal project that counts toward it. */
    private static final int MAX_LIVE_PROJECTS = 3;

    /** Deliberately permissive — the authority on deliverability is the user lookup inside
     *  {@code InviteGroupMemberUseCase}. This only rejects text that plainly isn't an address, so
     *  the assistant re-asks instead of storing a card that can't work. */
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s.]+\\.[^@\\s]+$");

    private final GroupRepository groupRepository;
    private final GroupProjectRepository groupProjectRepository;
    private final ProjectRepository projectRepository;

    /** A validated, ready-to-confirm group change: what to show, and what to run on "yes". */
    public record Preview(ChatResponse.GroupChangeSuggestion suggestion,
                          PendingChatAction action) {}

    /**
     * Either a card, or the reason there isn't one — same contract as
     * {@code ProjectChangeService.Outcome}, and for the same reason: a silently dropped group
     * operation left the model's reply ("I'll create that group project for you") as the last word
     * on a turn where nothing was going to happen. The blockers here are all things the user can
     * act on — lock the group first, you're not the admin, this group already has a project — so
     * the note names the missing step.
     */
    public record Outcome(Preview preview, String note) {
        static Outcome of(Preview preview) { return new Outcome(preview, null); }
        static Outcome incomplete() { return new Outcome(null, null); }
        static Outcome blocked(String note) { return new Outcome(null, note); }
        public Optional<Preview> asOptional() { return Optional.ofNullable(preview); }
    }

    /**
     * Validates a draft and builds its preview.
     *
     * @param draft      the assistant's accumulated draft; null/incomplete yields empty
     * @param messageLow the lowercased user message, used only to pick the reason's language
     * @return the preview, or empty when the draft isn't yet complete/legal enough to confirm
     */
    public Optional<Preview> preview(UUID userId, JsonSchemaGuardrail.GroupDraft draft,
                                     String messageLow) {
        return evaluate(userId, draft, messageLow).asOptional();
    }

    /** {@link #preview} plus the blocking reason, for callers that must not leave a false
     *  success claim standing. */
    public Outcome evaluate(UUID userId, JsonSchemaGuardrail.GroupDraft draft, String messageLow) {
        if (draft == null || !draft.complete()) return Outcome.incomplete();

        ChatResponse.GroupChangeOperation operation = parseOperation(draft.operation());
        if (operation == null) return Outcome.incomplete();

        boolean english = MessageLanguageDetector.isEnglish(messageLow);
        try {
            return switch (operation) {
                case CREATE_GROUP -> previewCreateGroup(draft, english);
                case INVITE_MEMBER -> previewInvite(userId, draft, english);
                case LOCK_GROUP -> previewLock(userId, draft, english);
                case CREATE_GROUP_PROJECT -> previewCreateGroupProject(userId, draft, english);
                case JOIN_GROUP_PROJECT -> previewJoin(userId, draft, english);
            };
        } catch (Exception e) {
            log.warn("GroupChangeService: could not preview {} for user {}: {}",
                    operation, userId, e.getMessage());
            return Outcome.incomplete();
        }
    }

    /** "You administer no group by that name" — the same answer for every admin-only operation. */
    private Outcome noAdministeredGroup(boolean english) {
        return Outcome.blocked(english
                ? "I couldn't find a group you administer that matches — only a group's admin can do "
                + "this. Tell me the group's name, or create one first."
                : "Mình không tìm thấy nhóm nào bạn làm quản trị viên khớp với yêu cầu — chỉ quản trị "
                + "viên mới làm được việc này. Bạn cho mình tên nhóm, hoặc tạo nhóm mới trước nhé.");
    }

    // ── CREATE_GROUP ──────────────────────────────────────────────────────────

    private Outcome previewCreateGroup(JsonSchemaGuardrail.GroupDraft draft, boolean english) {
        String name = trim(draft.name(), MAX_NAME_LENGTH);
        if (name == null) return Outcome.incomplete();
        String description = trim(draft.description(), MAX_NAME_LENGTH);

        var suggestion = ChatResponse.GroupChangeSuggestion.builder()
                .operation(ChatResponse.GroupChangeOperation.CREATE_GROUP)
                .groupName(name)
                .impact(english
                        ? "You become the group's admin and can start inviting members."
                        : "Bạn trở thành quản trị viên của nhóm và có thể bắt đầu mời thành viên.")
                .reason(english
                        ? String.format("Create the savings group '%s'.", name)
                        : String.format("Tạo nhóm tiết kiệm '%s'.", name))
                .build();

        var action = PendingChatAction.builder()
                .type(PendingChatAction.Type.GROUP_CREATE)
                .groupName(name)
                .groupDescription(description)
                .build();

        return Outcome.of(new Preview(suggestion, action));
    }

    // ── INVITE_MEMBER ─────────────────────────────────────────────────────────

    private Outcome previewInvite(UUID userId, JsonSchemaGuardrail.GroupDraft draft,
                                  boolean english) {
        String email = draft.email() == null ? null : draft.email().trim().toLowerCase();
        if (email == null || !EMAIL.matcher(email).matches()) return Outcome.incomplete();

        Group group = resolveAdministeredGroup(userId, draft.groupId(), draft.name());
        if (group == null) return noAdministeredGroup(english);
        // Once locked, the membership list is closed — inviting would be rejected downstream.
        if (group.getStatus() != GroupStatus.FORMING) {
            return Outcome.blocked(english
                    ? String.format("Group '%s' is already locked, so its member list is closed and no "
                    + "further invitations can be sent.", group.getName())
                    : String.format("Nhóm '%s' đã khoá nên danh sách thành viên đã chốt, không mời thêm "
                    + "được nữa.", group.getName()));
        }

        var suggestion = ChatResponse.GroupChangeSuggestion.builder()
                .operation(ChatResponse.GroupChangeOperation.INVITE_MEMBER)
                .groupId(group.getId().value().toString())
                .groupName(group.getName())
                .email(email)
                .impact(english
                        ? "An invitation email is sent to that address; the link expires in 7 days. "
                        + "They must already have a SmartMoney account registered with it."
                        : "Một email lời mời sẽ được gửi tới địa chỉ đó, liên kết hết hạn sau 7 ngày. "
                        + "Người nhận phải đã có tài khoản SmartMoney đăng ký bằng email này.")
                .reason(english
                        ? String.format("Invite %s to group '%s'.", email, group.getName())
                        : String.format("Mời %s vào nhóm '%s'.", email, group.getName()))
                .build();

        var action = PendingChatAction.builder()
                .type(PendingChatAction.Type.GROUP_INVITE_MEMBER)
                .groupId(group.getId().value().toString())
                .groupName(group.getName())
                .inviteEmail(email)
                .build();

        return Outcome.of(new Preview(suggestion, action));
    }

    // ── LOCK_GROUP ────────────────────────────────────────────────────────────

    private Outcome previewLock(UUID userId, JsonSchemaGuardrail.GroupDraft draft,
                                boolean english) {
        Group group = resolveAdministeredGroup(userId, draft.groupId(), draft.name());
        if (group == null) return noAdministeredGroup(english);
        if (group.getStatus() != GroupStatus.FORMING) {
            return Outcome.blocked(english
                    ? String.format("Group '%s' is already locked — nothing left to do here.", group.getName())
                    : String.format("Nhóm '%s' đã được khoá từ trước rồi, không cần khoá lại.", group.getName()));
        }

        int joined = group.joinedMembers().size();
        var suggestion = ChatResponse.GroupChangeSuggestion.builder()
                .operation(ChatResponse.GroupChangeOperation.LOCK_GROUP)
                .groupId(group.getId().value().toString())
                .groupName(group.getName())
                .impact(english
                        ? String.format("Membership closes at %d member(s). Pending invitations can "
                        + "no longer be accepted, and the group becomes eligible for a project.", joined)
                        : String.format("Danh sách chốt ở %d thành viên. Các lời mời chưa phản hồi sẽ "
                        + "không nhận được nữa, và nhóm đủ điều kiện tạo dự án.", joined))
                .reason(english
                        ? String.format("Lock group '%s'.", group.getName())
                        : String.format("Khoá nhóm '%s'.", group.getName()))
                .build();

        var action = PendingChatAction.builder()
                .type(PendingChatAction.Type.GROUP_LOCK)
                .groupId(group.getId().value().toString())
                .groupName(group.getName())
                .build();

        return Outcome.of(new Preview(suggestion, action));
    }

    // ── CREATE_GROUP_PROJECT ──────────────────────────────────────────────────

    private Outcome previewCreateGroupProject(UUID userId, JsonSchemaGuardrail.GroupDraft draft,
                                              boolean english) {
        String name = trim(draft.name(), MAX_NAME_LENGTH);
        if (name == null) return Outcome.incomplete();

        BigDecimal target = draft.targetAmount();
        if (target == null || target.compareTo(BigDecimal.ZERO) <= 0) return Outcome.incomplete();

        Integer months = draft.totalMonths();
        if (months == null || months < MIN_TOTAL_MONTHS || months > MAX_TOTAL_MONTHS) {
            return Outcome.incomplete();
        }

        // The group is identified by id from the context block; a name match here would collide
        // with the PROJECT's name, which is also in this draft.
        Group group = resolveAdministeredGroup(userId, draft.groupId(), null);
        if (group == null) return noAdministeredGroup(english);
        // Both are hard rejections in CreateGroupProjectUseCase. Naming the missing step is the
        // whole point: "lock the group first" is a one-tap fix, and staying silent made the turn
        // read as though the group project had been created.
        if (group.getStatus() != GroupStatus.LOCKED) {
            return Outcome.blocked(english
                    ? String.format("Group '%s' is still forming. Lock it first — that closes the member "
                    + "list — and then its group project can be created. Want me to lock it?", group.getName())
                    : String.format("Nhóm '%s' vẫn đang gom thành viên. Cần KHOÁ nhóm trước (chốt danh "
                    + "sách thành viên) rồi mới tạo được dự án nhóm. Bạn muốn mình khoá nhóm luôn không?",
                    group.getName()));
        }
        if (groupProjectRepository.findByGroupId(group.getId().value()).isPresent()) {
            return Outcome.blocked(english
                    ? String.format("Group '%s' already has a group project — each group can only have one. "
                    + "Create another group for a second goal.", group.getName())
                    : String.format("Nhóm '%s' đã có một dự án nhóm rồi — mỗi nhóm chỉ gắn với đúng một "
                    + "dự án. Muốn làm mục tiêu thứ hai thì cần tạo nhóm khác.", group.getName()));
        }

        int memberCount = Math.max(1, group.joinedMembers().size());
        BigDecimal perMemberMonthly = target
                .divide(BigDecimal.valueOf((long) memberCount * months), 0, RoundingMode.CEILING);

        String currency = draft.currency() == null ? "VND" : draft.currency();

        var suggestion = ChatResponse.GroupChangeSuggestion.builder()
                .operation(ChatResponse.GroupChangeOperation.CREATE_GROUP_PROJECT)
                .groupId(group.getId().value().toString())
                .groupName(group.getName())
                .projectName(name)
                .targetAmount(target.longValue())
                .totalMonths(months)
                .impact(english
                        ? String.format("Split evenly that is about %,d VND/month for each of %d member(s). "
                        + "Members who can't cover their share trigger a sponsorship survey first.",
                        perMemberMonthly.longValue(), memberCount)
                        : String.format("Chia đều là khoảng %,d VND/tháng cho mỗi thành viên trong %d người. "
                        + "Thành viên không gánh nổi phần của mình sẽ kích hoạt khảo sát đóng góp giúp trước.",
                        perMemberMonthly.longValue(), memberCount))
                .reason(english
                        ? String.format("Create group project '%s' for '%s': %,d VND over %d months.",
                        name, group.getName(), target.longValue(), months)
                        : String.format("Tạo dự án nhóm '%s' cho nhóm '%s': %,d VND trong %d tháng.",
                        name, group.getName(), target.longValue(), months))
                .build();

        var action = PendingChatAction.builder()
                .type(PendingChatAction.Type.GROUP_PROJECT_CREATE)
                .groupId(group.getId().value().toString())
                .groupName(group.getName())
                .name(name)
                .description(trim(draft.description(), MAX_NAME_LENGTH))
                .targetAmount(target)
                .currency(currency)
                .totalMonths(months)
                .build();

        return Outcome.of(new Preview(suggestion, action));
    }

    // ── JOIN_GROUP_PROJECT ────────────────────────────────────────────────────

    private Outcome previewJoin(UUID userId, JsonSchemaGuardrail.GroupDraft draft,
                                boolean english) {
        GroupProject gp = resolveJoinableGroupProject(userId, draft.groupProjectId());
        if (gp == null) {
            return Outcome.blocked(english
                    ? "I couldn't find that group project among the groups you belong to."
                    : "Mình không tìm thấy dự án nhóm đó trong các nhóm bạn đang tham gia.");
        }
        if (gp.getStatus() != GroupProjectStatus.ACTIVE) {
            return Outcome.blocked(english
                    ? String.format("Group project '%s' isn't open for joining right now (%s).",
                    gp.getName(), gp.getStatus())
                    : String.format("Dự án nhóm '%s' hiện chưa mở để tham gia (trạng thái %s).",
                    gp.getName(), gp.getStatus()));
        }

        List<Project> mine = projectRepository.findAllByUser(UserId.of(userId.toString()));
        boolean alreadyJoined = mine.stream()
                .anyMatch(p -> gp.getId().value().equals(p.getGroupProjectId())
                        && p.getStatus() != ProjectStatus.ABANDONED);
        if (alreadyJoined) {
            return Outcome.blocked(english
                    ? String.format("You've already joined '%s' — your linked personal project is in your "
                    + "project list.", gp.getName())
                    : String.format("Bạn đã tham gia '%s' rồi — dự án cá nhân liên kết đang nằm trong danh "
                    + "sách dự án của bạn.", gp.getName()));
        }

        List<Project> live = mine.stream()
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE || p.getStatus() == ProjectStatus.FROZEN)
                .toList();
        if (live.size() >= MAX_LIVE_PROJECTS) {
            return Outcome.blocked(english
                    ? String.format("Joining opens a linked personal project, but you already have %d "
                    + "running — the maximum. Finish or remove one first.", live.size())
                    : String.format("Tham gia sẽ mở một dự án cá nhân liên kết, nhưng bạn đang có %d dự án "
                    + "chạy song song — đã chạm giới hạn. Hãy hoàn tất hoặc xoá bớt một dự án trước.",
                    live.size()));
        }

        Set<ProjectPriority> taken = live.stream().map(Project::getPriority).collect(Collectors.toSet());
        ProjectPriority priority = parsePriority(draft.priority());
        if (priority == null || taken.contains(priority)) {
            priority = Arrays.stream(ProjectPriority.values())
                    .filter(p -> !taken.contains(p))
                    .findFirst()
                    .orElse(null);
        }
        if (priority == null) return Outcome.incomplete();

        var suggestion = ChatResponse.GroupChangeSuggestion.builder()
                .operation(ChatResponse.GroupChangeOperation.JOIN_GROUP_PROJECT)
                .groupProjectId(gp.getId().value().toString())
                .projectName(gp.getName())
                .priority(priority.name())
                .impact(english
                        ? "A linked personal project is created to track your own share."
                        : "Một dự án cá nhân liên kết sẽ được tạo để theo dõi phần đóng góp của bạn.")
                // The structured `priority` field above keeps the enum name for the client; this
                // sentence is read by the user, so it spells the level out in words.
                .reason(english
                        ? String.format("Join group project '%s' at %s.", gp.getName(),
                        FinancialTermFormatter.projectPriority(priority, true))
                        : String.format("Tham gia dự án nhóm '%s' với %s.", gp.getName(),
                        FinancialTermFormatter.projectPriority(priority, false)))
                .build();

        var action = PendingChatAction.builder()
                .type(PendingChatAction.Type.GROUP_PROJECT_JOIN)
                .groupProjectId(gp.getId().value().toString())
                .name(gp.getName())
                .priority(priority.name())
                .build();

        return Outcome.of(new Preview(suggestion, action));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Resolves a group the actor administers, by id first then by name. Non-admin groups resolve
     * to null on purpose: every operation this service builds is admin-only downstream, so a
     * member asking for one should get an explanation, not a card that will be rejected.
     *
     * <p>When the user administers exactly one group and named none, that group is used — the
     * overwhelmingly common case, and unambiguous by construction.
     */
    private Group resolveAdministeredGroup(UUID userId, String groupId, String name) {
        List<Group> administered = groupRepository.findAllByMember(UserId.of(userId.toString())).stream()
                .filter(g -> g.getStatus() != GroupStatus.DISSOLVED)
                .filter(g -> g.getAdminId().equals(UserId.of(userId.toString())))
                .toList();
        if (administered.isEmpty()) return null;

        if (groupId != null) {
            Optional<Group> byId = administered.stream()
                    .filter(g -> g.getId().value().toString().equals(groupId))
                    .findFirst();
            if (byId.isPresent()) return byId.get();
        }
        if (name != null) {
            String needle = name.trim().toLowerCase();
            List<Group> byName = administered.stream()
                    .filter(g -> g.getName().toLowerCase().equals(needle))
                    .toList();
            if (byName.size() == 1) return byName.get(0);
        }
        return administered.size() == 1 ? administered.get(0) : null;
    }

    /** The group project of a group the user belongs to (admin or member — joining is open to
     *  every member, unlike the admin-only operations above). */
    private GroupProject resolveJoinableGroupProject(UUID userId, String groupProjectId) {
        List<Group> mine = groupRepository.findAllByMember(UserId.of(userId.toString())).stream()
                .filter(g -> g.getStatus() != GroupStatus.DISSOLVED)
                .toList();
        List<GroupProject> candidates = mine.stream()
                .map(g -> groupProjectRepository.findByGroupId(g.getId().value()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        if (groupProjectId != null) {
            Optional<GroupProject> byId = candidates.stream()
                    .filter(gp -> gp.getId().value().toString().equals(groupProjectId))
                    .findFirst();
            if (byId.isPresent()) return byId.get();
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private ChatResponse.GroupChangeOperation parseOperation(String raw) {
        if (raw == null) return null;
        try {
            return ChatResponse.GroupChangeOperation.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ProjectPriority parsePriority(String raw) {
        if (raw == null) return null;
        try {
            return ProjectPriority.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
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
