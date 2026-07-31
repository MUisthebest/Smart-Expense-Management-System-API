package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.requests.project.ProjectFilterRequest;
import com.example.smartmoney.application.dtos.responses.project.ProjectListItemResponse;
import com.example.smartmoney.application.dtos.responses.project.ProjectParticipantResponse;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.valueobjects.Avatar;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ListMyProjectsUseCase — group project participants on the list")
class ListMyProjectsUseCaseTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;

    private ListMyProjectsUseCase useCase;

    private final UUID myId = UUID.randomUUID();
    private final UUID friendId = UUID.randomUUID();
    private final UUID leaverId = UUID.randomUUID();
    private final UUID groupProjectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new ListMyProjectsUseCase(projectRepository, userRepository);
    }

    private Project personalProject(UUID ownerId) {
        return Project.createNew(UserId.of(ownerId.toString()),
                com.example.smartmoney.domain.models.project.enums.ProjectType.PERSONAL,
                ProjectPriority.MEDIUM, "Solo trip", null,
                new Money(new BigDecimal("10000000"), "VND"), LocalDate.now().plusMonths(6));
    }

    private Project subPersonalProject(UUID ownerId) {
        return Project.createSubPersonal(UserId.of(ownerId.toString()), ProjectPriority.MEDIUM,
                "Group trip", null, new Money(new BigDecimal("5000000"), "VND"),
                LocalDate.now().plusMonths(6), groupProjectId);
    }

    private User user(UUID id, String username, String fullName, String avatarUrl) {
        return User.builder().id(id).username(username).fullName(fullName)
                .avatar(new Avatar(avatarUrl)).build();
    }

    @Test
    @DisplayName("personal project → no groupProjectId, no participants, no user lookup")
    void personalProjectHasNoParticipants() {
        when(projectRepository.findAllByUser(any(UserId.class), any(ProjectFilterRequest.class)))
                .thenReturn(List.of(personalProject(myId)));

        List<ProjectListItemResponse> result = useCase.execute(myId.toString(),
                new ProjectFilterRequest(null, null, null, null));

        assertEquals(1, result.size());
        assertNull(result.get(0).getGroupProjectId());
        assertNull(result.get(0).getParticipants());
        verify(userRepository, never()).findAllByIds(any());
    }

    @Test
    @DisplayName("group sub-project → participants carry each member's name and avatar; leavers excluded")
    void groupProjectListsParticipantsWithAvatars() {
        Project mine = subPersonalProject(myId);
        Project friends = subPersonalProject(friendId);
        Project left = subPersonalProject(leaverId);
        left.abandon(UserId.of(leaverId.toString())); // member left the group project

        when(projectRepository.findAllByUser(any(UserId.class), any(ProjectFilterRequest.class)))
                .thenReturn(List.of(mine));
        when(projectRepository.findAllByGroupProjectId(groupProjectId))
                .thenReturn(List.of(mine, friends, left));
        when(userRepository.findAllByIds(any())).thenReturn(List.of(
                user(myId, "an", "Nguyen Phuc An", "https://cdn.example.com/an.png"),
                user(friendId, "binh", "Tran Binh", "https://cdn.example.com/binh.png")));

        List<ProjectListItemResponse> result = useCase.execute(myId.toString(),
                new ProjectFilterRequest(null, null, null, null));

        assertEquals(1, result.size());
        ProjectListItemResponse item = result.get(0);
        assertEquals(groupProjectId.toString(), item.getGroupProjectId());
        assertEquals(2, item.getParticipants().size());

        ProjectParticipantResponse me = item.getParticipants().stream()
                .filter(pt -> pt.getUserId().equals(myId.toString())).findFirst().orElseThrow();
        assertEquals("an", me.getUsername());
        assertEquals("Nguyen Phuc An", me.getFullName());
        assertEquals("https://cdn.example.com/an.png", me.getAvatarUrl());

        assertTrue(item.getParticipants().stream()
                .noneMatch(pt -> pt.getUserId().equals(leaverId.toString())));
    }
}
