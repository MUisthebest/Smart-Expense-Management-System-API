package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.domain.models.group.GroupProject;
import com.example.smartmoney.domain.models.group.enums.GroupProjectStatus;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectContribution;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.ContributionId;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupProjectCompletionService — completion/expiry off the settlement cron")
class GroupProjectCompletionServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private GroupProjectRepository groupProjectRepository;

    private GroupProjectCompletionService service;

    @BeforeEach
    void setUp() {
        service = new GroupProjectCompletionService(projectRepository, groupProjectRepository);
        lenient().when(groupProjectRepository.save(any(GroupProject.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private GroupProject activeGroupProject() {
        return GroupProject.createNew(UUID.randomUUID(), "Group goal", null,
                new BigDecimal("1000"), new BigDecimal("1000"), "VND", 6, LocalDate.now().plusMonths(6));
    }

    private Project sub(String target, String contributed, ProjectStatus status) {
        List<ProjectContribution> contribs = new ArrayList<>();
        if (contributed != null) {
            contribs.add(new ProjectContribution(ContributionId.newId(),
                    UserId.of(UUID.randomUUID().toString()),
                    new Money(new BigDecimal(contributed), "VND"), null, Instant.now()));
        }
        return Project.rehydrate(ProjectId.newId(), UserId.of(UUID.randomUUID().toString()),
                ProjectType.PERSONAL, status, ProjectPriority.MEDIUM, "sub", null,
                new Money(new BigDecimal(target), "VND"), LocalDate.now().plusMonths(3),
                UUID.randomUUID(), contribs, null, Instant.now());
    }

    @Test
    @DisplayName("Completes when participating aggregate reaches the participating target")
    void completesWhenTargetMet() {
        GroupProject gp = activeGroupProject();
        List<Project> subs = List.of(
                sub("500", "500", ProjectStatus.ACTIVE),
                sub("500", "500", ProjectStatus.ACTIVE));

        GroupProject result = service.evaluate(gp, subs);

        assertEquals(GroupProjectStatus.COMPLETED, result.getStatus());
        verify(groupProjectRepository).save(gp);
    }

    @Test
    @DisplayName("Expires when no participating members remain")
    void expiresWhenAllDroppedOut() {
        GroupProject gp = activeGroupProject();
        List<Project> subs = List.of(
                sub("500", null, ProjectStatus.EXPIRED),
                sub("500", null, ProjectStatus.ABANDONED));

        GroupProject result = service.evaluate(gp, subs);

        assertEquals(GroupProjectStatus.EXPIRED, result.getStatus());
        verify(groupProjectRepository).save(gp);
    }

    @Test
    @DisplayName("Dropped members shrink the goal so the rest can still complete it")
    void droppedMemberShrinksGoal() {
        GroupProject gp = activeGroupProject();
        // Participating: one member at 500/500. A dropped (EXPIRED) member's 500 target leaves
        // both sides, so required=500, aggregate=500 → complete.
        List<Project> subs = List.of(
                sub("500", "500", ProjectStatus.ACTIVE),
                sub("500", "0", ProjectStatus.EXPIRED));

        GroupProject result = service.evaluate(gp, subs);

        assertEquals(GroupProjectStatus.COMPLETED, result.getStatus());
    }

    @Test
    @DisplayName("No-op while participating aggregate is below target")
    void noopWhenBelowTarget() {
        GroupProject gp = activeGroupProject();
        List<Project> subs = List.of(sub("1000", "100", ProjectStatus.ACTIVE));

        GroupProject result = service.evaluate(gp, subs);

        assertEquals(GroupProjectStatus.ACTIVE, result.getStatus());
        verify(groupProjectRepository, never()).save(any());
    }

    @Test
    @DisplayName("No-op when the group project is already terminal")
    void noopWhenTerminal() {
        GroupProject gp = GroupProject.rehydrate(
                com.example.smartmoney.domain.models.group.valueobjects.GroupProjectId.newId(),
                UUID.randomUUID(), "Group goal", null, new BigDecimal("1000"), new BigDecimal("1000"),
                "VND", 6, LocalDate.now().plusMonths(6), GroupProjectStatus.COMPLETED,
                Instant.now(), Instant.now());
        List<Project> subs = List.of(sub("500", "500", ProjectStatus.ACTIVE));

        GroupProject result = service.evaluate(gp, subs);

        assertEquals(GroupProjectStatus.COMPLETED, result.getStatus());
        verify(groupProjectRepository, never()).save(any());
    }
}
