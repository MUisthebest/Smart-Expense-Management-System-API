package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.services.ProjectRefundService;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.GroupProject;
import com.example.smartmoney.domain.models.group.enums.GroupStatus;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.GroupProjectSponsorshipRequestRepository;
import com.example.smartmoney.domain.repositories.GroupRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.services.SafeSpendingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DissolveGroupProjectUseCase")
class DissolveGroupProjectUseCaseTest {

    @Mock private GroupProjectRepository groupProjectRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private GroupProjectSponsorshipRequestRepository sponsorshipRequestRepository;
    @Mock private SafeSpendingService safeSpendingService;
    @Mock private ProjectRefundService projectRefundService;

    private DissolveGroupProjectUseCase useCase;

    private final UUID adminUuid = UUID.randomUUID();
    private final UUID groupUuid = UUID.randomUUID();

    private GroupProject groupProject;

    @BeforeEach
    void setUp() {
        useCase = new DissolveGroupProjectUseCase(groupProjectRepository, groupRepository,
                projectRepository, sponsorshipRequestRepository, safeSpendingService, projectRefundService);

        groupProject = GroupProject.createNew(groupUuid, "Group goal", null,
                new BigDecimal("3000"), new BigDecimal("3000"), "VND", 6, LocalDate.now().plusMonths(6));
    }

    private void stubLookups() {
        when(groupProjectRepository.findById(groupProject.getId())).thenReturn(Optional.of(groupProject));
        when(groupRepository.findById(GroupId.of(groupUuid.toString()))).thenReturn(Optional.of(
                Group.rehydrate(GroupId.of(groupUuid.toString()), "Group", null, new UserId(adminUuid),
                        GroupStatus.LOCKED, List.of(), Instant.now(), Instant.now())));
    }

    private Project sub(ProjectStatus status) {
        return Project.rehydrate(ProjectId.newId(), new UserId(UUID.randomUUID()), ProjectType.PERSONAL,
                status, ProjectPriority.MEDIUM, "Sub", null, new Money(new BigDecimal("1000"), "VND"),
                LocalDate.now().plusMonths(6), groupProject.getId().value(), List.of(), null, Instant.now());
    }

    private void execute() {
        useCase.execute(adminUuid.toString(), groupProject.getId().value().toString());
    }

    @Test
    @DisplayName("each deleted sub-project is refunded before it is deleted")
    void refundsEachDeletedSubProject() {
        Project active = sub(ProjectStatus.ACTIVE);
        Project frozen = sub(ProjectStatus.FROZEN);
        stubLookups();
        when(projectRepository.findAllByGroupProjectId(groupProject.getId().value()))
                .thenReturn(List.of(active, frozen));

        execute();

        // Refund must precede the delete: once the project row is gone the cancellation
        // has no aggregate left to read its owner, name, or status from.
        InOrder order = inOrder(projectRefundService, projectRepository);
        order.verify(projectRefundService).refundOnCancellation(active);
        order.verify(projectRepository).deleteById(active.getId());
        order.verify(projectRefundService).refundOnCancellation(frozen);
        order.verify(projectRepository).deleteById(frozen.getId());
    }

    @Test
    @DisplayName("preserved sub-projects (completed, expired, abandoned) are neither refunded nor deleted")
    void preservedSubProjectsAreNotRefunded() {
        Project completed = sub(ProjectStatus.COMPLETED);
        Project expired = sub(ProjectStatus.EXPIRED);
        Project abandoned = sub(ProjectStatus.ABANDONED);
        stubLookups();
        when(projectRepository.findAllByGroupProjectId(groupProject.getId().value()))
                .thenReturn(List.of(completed, expired, abandoned));

        execute();

        verify(projectRefundService, never()).refundOnCancellation(any());
        verify(projectRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("a non-admin actor dissolves nothing and refunds nothing")
    void nonAdmin_refundsNothing() {
        stubLookups();

        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID().toString(),
                groupProject.getId().value().toString()))
                .isInstanceOf(ProjectException.class);

        verify(projectRefundService, never()).refundOnCancellation(any());
        verify(projectRepository, never()).deleteById(any());
    }
}
