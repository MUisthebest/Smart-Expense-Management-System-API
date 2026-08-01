package com.example.smartmoney.application.services;

import com.example.smartmoney.application.dtos.chat.PendingChatAction;
import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.application.guardrails.JsonSchemaGuardrail;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.GroupProject;
import com.example.smartmoney.domain.models.group.enums.GroupStatus;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.GroupRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the second reported gap: asking the chatbot to create a group project only ever produced
 * a walkthrough of the app's screens, because no {@code PendingChatAction.Type} existed for it.
 * These tests assert the operations are now real actions, and that the preconditions which would
 * otherwise blow up inside {@code CreateGroupProjectUseCase} are caught before a card is offered.
 */
@DisplayName("GroupChangeService")
class GroupChangeServiceTest {

    private final GroupRepository groupRepository = mock(GroupRepository.class);
    private final GroupProjectRepository groupProjectRepository = mock(GroupProjectRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);

    private final GroupChangeService service =
            new GroupChangeService(groupRepository, groupProjectRepository, projectRepository);

    private final UUID userId = UUID.randomUUID();
    private final UUID groupUuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(groupRepository.findAllByMember(any(UserId.class))).thenReturn(List.of());
        when(groupProjectRepository.findByGroupId(any(UUID.class))).thenReturn(Optional.empty());
        when(projectRepository.findAllByUser(any(UserId.class))).thenReturn(List.of());
    }

    private Group group(GroupStatus status, boolean adminIsUser) {
        Group g = mock(Group.class);
        GroupId id = mock(GroupId.class);
        when(id.value()).thenReturn(groupUuid);
        when(g.getId()).thenReturn(id);
        when(g.getName()).thenReturn("Quỹ du lịch");
        when(g.getStatus()).thenReturn(status);
        when(g.getAdminId()).thenReturn(adminIsUser
                ? UserId.of(userId.toString()) : UserId.of(UUID.randomUUID().toString()));
        when(g.joinedMembers()).thenReturn(List.of());
        return g;
    }

    private void withGroup(Group g) {
        when(groupRepository.findAllByMember(any(UserId.class))).thenReturn(List.of(g));
    }

    private JsonSchemaGuardrail.GroupDraft draft(String operation, String name, String email,
                                                 BigDecimal target, Integer months) {
        return new JsonSchemaGuardrail.GroupDraft(operation, groupUuid.toString(), null, name,
                null, email, target, "VND", months, null, true);
    }

    @Test
    @DisplayName("creating a group is a real GROUP_CREATE action, not a walkthrough")
    void createGroupBecomesAnAction() {
        var d = new JsonSchemaGuardrail.GroupDraft("CREATE_GROUP", null, null, "Quỹ du lịch",
                "Đi Đà Lạt", null, null, null, null, null, true);

        var preview = service.preview(userId, d, "tạo nhóm tiết kiệm quỹ du lịch");

        assertThat(preview).isPresent();
        assertThat(preview.get().action().getType()).isEqualTo(PendingChatAction.Type.GROUP_CREATE);
        assertThat(preview.get().action().getGroupName()).isEqualTo("Quỹ du lịch");
        assertThat(preview.get().suggestion().getOperation())
                .isEqualTo(ChatResponse.GroupChangeOperation.CREATE_GROUP);
    }

    @Test
    @DisplayName("inviting by email is a real GROUP_INVITE_MEMBER action carrying the address")
    void inviteBecomesAnAction() {
        withGroup(group(GroupStatus.FORMING, true));

        var preview = service.preview(userId,
                draft("INVITE_MEMBER", null, "an@example.com", null, null),
                "mời an@example.com vào nhóm");

        assertThat(preview).isPresent();
        PendingChatAction action = preview.get().action();
        assertThat(action.getType()).isEqualTo(PendingChatAction.Type.GROUP_INVITE_MEMBER);
        assertThat(action.getInviteEmail()).isEqualTo("an@example.com");
        assertThat(action.getGroupId()).isEqualTo(groupUuid.toString());
        // The card has to say an email actually goes out and that the link expires, since neither
        // is visible to the user until it lands in their inbox.
        assertThat(preview.get().suggestion().getImpact()).contains("7 ngày");
    }

    @Test
    @DisplayName("a malformed email yields no card, so the assistant re-asks instead of storing a dud")
    void malformedEmailYieldsNoCard() {
        withGroup(group(GroupStatus.FORMING, true));

        assertThat(service.preview(userId,
                draft("INVITE_MEMBER", null, "an-at-example", null, null),
                "mời an vào nhóm")).isEmpty();
    }

    @Test
    @DisplayName("a non-admin gets no card — every admin-only op would be rejected downstream")
    void nonAdminYieldsNoCard() {
        withGroup(group(GroupStatus.FORMING, false));

        assertThat(service.preview(userId,
                draft("INVITE_MEMBER", null, "an@example.com", null, null),
                "mời an@example.com vào nhóm")).isEmpty();
    }

    @Test
    @DisplayName("creating the group project works once the group is LOCKED and has none yet")
    void createGroupProjectBecomesAnAction() {
        withGroup(group(GroupStatus.LOCKED, true));

        var preview = service.preview(userId,
                draft("CREATE_GROUP_PROJECT", "Mua đất", null, BigDecimal.valueOf(240_000_000), 12),
                "tạo dự án nhóm mua đất 240 triệu trong 12 tháng");

        assertThat(preview).isPresent();
        PendingChatAction action = preview.get().action();
        assertThat(action.getType()).isEqualTo(PendingChatAction.Type.GROUP_PROJECT_CREATE);
        assertThat(action.getName()).isEqualTo("Mua đất");
        assertThat(action.getTargetAmount()).isEqualByComparingTo("240000000");
        assertThat(action.getTotalMonths()).isEqualTo(12);
        assertThat(action.getGroupId()).isEqualTo(groupUuid.toString());
    }

    @Test
    @DisplayName("a FORMING group yields no card — LOCKED first is a hard precondition, not a hint")
    void groupProjectNeedsLockedGroup() {
        withGroup(group(GroupStatus.FORMING, true));

        assertThat(service.preview(userId,
                draft("CREATE_GROUP_PROJECT", "Mua đất", null, BigDecimal.valueOf(240_000_000), 12),
                "tạo dự án nhóm mua đất")).isEmpty();
    }

    @Test
    @DisplayName("a group that already has a project yields no card — one project per group")
    void groupProjectIsOnePerGroup() {
        withGroup(group(GroupStatus.LOCKED, true));
        when(groupProjectRepository.findByGroupId(groupUuid))
                .thenReturn(Optional.of(mock(GroupProject.class)));

        assertThat(service.preview(userId,
                draft("CREATE_GROUP_PROJECT", "Mua đất", null, BigDecimal.valueOf(240_000_000), 12),
                "tạo dự án nhóm mua đất")).isEmpty();
    }

    @Test
    @DisplayName("totalMonths outside 1..60 yields no card, matching CreateGroupProjectRequest's bounds")
    void totalMonthsIsBounded() {
        withGroup(group(GroupStatus.LOCKED, true));

        assertThat(service.preview(userId,
                draft("CREATE_GROUP_PROJECT", "Mua đất", null, BigDecimal.valueOf(240_000_000), 61),
                "tạo dự án nhóm mua đất")).isEmpty();
    }

    @Test
    @DisplayName("an incomplete draft yields nothing regardless of operation")
    void incompleteDraftYieldsNoCard() {
        withGroup(group(GroupStatus.LOCKED, true));
        var d = new JsonSchemaGuardrail.GroupDraft("CREATE_GROUP_PROJECT", groupUuid.toString(),
                null, "Mua đất", null, null, BigDecimal.valueOf(240_000_000), "VND", 12, null, false);

        assertThat(service.preview(userId, d, "tạo dự án nhóm mua đất")).isEmpty();
    }

    @Test
    @DisplayName("null draft (every non-group turn) yields nothing")
    void nullDraftYieldsNoCard() {
        assertThat(service.preview(userId, null, "tôi tiêu bao nhiêu tháng này")).isEmpty();
    }
}
