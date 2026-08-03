package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.services.ProjectRefundService;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectHistoryRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectTrackingRepository;
import com.example.smartmoney.domain.services.SafeSpendingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeleteProjectUseCase")
class DeleteProjectUseCaseTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectTrackingRepository projectTrackingRepository;
    @Mock private ProjectHistoryRepository projectHistoryRepository;
    @Mock private SafeSpendingService safeSpendingService;
    @Mock private ProjectRefundService projectRefundService;

    private DeleteProjectUseCase useCase;

    private final UUID ownerUuid = UUID.randomUUID();
    private final ProjectId pid = ProjectId.newId();

    @BeforeEach
    void setUp() {
        useCase = new DeleteProjectUseCase(
                projectRepository, projectTrackingRepository, projectHistoryRepository,
                safeSpendingService, projectRefundService);
    }

    private Project project(UUID groupProjectId, ProjectStatus status) {
        return Project.rehydrate(pid, new UserId(ownerUuid), ProjectType.PERSONAL, status,
                ProjectPriority.MEDIUM, "Trip", null, new Money(new BigDecimal("3000"), "USD"),
                LocalDate.now().plusMonths(5), groupProjectId, List.of(), null, Instant.now());
    }

    @Test
    @DisplayName("standalone personal project is hard-deleted (history, tracking, project)")
    void standalone_hardDeletes() {
        when(projectRepository.findById(pid)).thenReturn(Optional.of(project(null, ProjectStatus.ACTIVE)));

        useCase.execute(ownerUuid.toString(), pid.toString());

        verify(projectHistoryRepository).deleteByProjectId(pid);
        verify(projectTrackingRepository).deleteByProjectId(pid);
        verify(projectRepository).deleteById(pid);
        verify(projectRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(safeSpendingService).recalculateAndSaveSafeSpending(new UserId(ownerUuid));
    }

    @Test
    @DisplayName("sub-personal project is abandoned, not hard-deleted")
    void subPersonal_abandons() {
        UUID groupProjectId = UUID.randomUUID();
        when(projectRepository.findById(pid)).thenReturn(Optional.of(project(groupProjectId, ProjectStatus.ACTIVE)));

        useCase.execute(ownerUuid.toString(), pid.toString());

        verify(projectRepository).save(org.mockito.ArgumentMatchers.argThat(
                p -> p.getStatus() == ProjectStatus.ABANDONED));
        verify(projectRepository, never()).deleteById(org.mockito.ArgumentMatchers.any());
        verify(projectHistoryRepository, never()).deleteByProjectId(org.mockito.ArgumentMatchers.any());
        verify(projectTrackingRepository, never()).deleteByProjectId(org.mockito.ArgumentMatchers.any());
        verify(safeSpendingService).recalculateAndSaveSafeSpending(new UserId(ownerUuid));
    }

    @Test
    @DisplayName("non-owner cannot delete")
    void nonOwner_forbidden() {
        when(projectRepository.findById(pid)).thenReturn(Optional.of(project(null, ProjectStatus.ACTIVE)));

        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID().toString(), pid.toString()))
                .isInstanceOf(ProjectException.class);

        verify(projectRepository, never()).deleteById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("already-abandoned sub-personal project is rejected")
    void subPersonalAlreadyAbandoned_rejected() {
        UUID groupProjectId = UUID.randomUUID();
        when(projectRepository.findById(pid)).thenReturn(Optional.of(project(groupProjectId, ProjectStatus.ABANDONED)));

        assertThatThrownBy(() -> useCase.execute(ownerUuid.toString(), pid.toString()))
                .isInstanceOf(ProjectException.class);

        verify(projectRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("standalone deletion proceeds even when already abandoned")
    void standaloneAlreadyAbandoned_stillHardDeletes() {
        when(projectRepository.findById(pid)).thenReturn(Optional.of(project(null, ProjectStatus.ABANDONED)));

        useCase.execute(ownerUuid.toString(), pid.toString());

        verify(projectRepository).deleteById(pid);
    }

    @Test
    @DisplayName("standalone deletion refunds the project's contributions")
    void standalone_refunds() {
        Project p = project(null, ProjectStatus.ACTIVE);
        when(projectRepository.findById(pid)).thenReturn(Optional.of(p));

        useCase.execute(ownerUuid.toString(), pid.toString());

        verify(projectRefundService).refundOnCancellation(p);
    }

    @Test
    @DisplayName("sub-personal abandonment refunds the project's contributions")
    void subPersonal_refunds() {
        Project p = project(UUID.randomUUID(), ProjectStatus.ACTIVE);
        when(projectRepository.findById(pid)).thenReturn(Optional.of(p));

        useCase.execute(ownerUuid.toString(), pid.toString());

        verify(projectRefundService).refundOnCancellation(p);
    }

    @Test
    @DisplayName("refund runs before abandon(), so the refund rule still sees the real status")
    void refundsBeforeStatusIsOverwritten() {
        // abandon() rewrites status to ABANDONED. If the refund ran afterwards it could no longer
        // tell a COMPLETED project (not refundable) from a cancelled one — so it must run first.
        Project p = project(UUID.randomUUID(), ProjectStatus.COMPLETED);
        when(projectRepository.findById(pid)).thenReturn(Optional.of(p));

        AtomicReference<ProjectStatus> statusAtRefund = new AtomicReference<>();
        when(projectRefundService.refundOnCancellation(p)).thenAnswer(inv -> {
            statusAtRefund.set(inv.getArgument(0, Project.class).getStatus());
            return BigDecimal.ZERO;
        });

        useCase.execute(ownerUuid.toString(), pid.toString());

        assertThat(statusAtRefund.get()).isEqualTo(ProjectStatus.COMPLETED);
    }

    @Test
    @DisplayName("an already-abandoned sub-personal project is rejected before any refund")
    void subPersonalAlreadyAbandoned_doesNotRefund() {
        when(projectRepository.findById(pid)).thenReturn(
                Optional.of(project(UUID.randomUUID(), ProjectStatus.ABANDONED)));

        assertThatThrownBy(() -> useCase.execute(ownerUuid.toString(), pid.toString()))
                .isInstanceOf(ProjectException.class);

        verify(projectRefundService, never()).refundOnCancellation(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("non-owner deletion refunds nothing")
    void nonOwner_doesNotRefund() {
        when(projectRepository.findById(pid)).thenReturn(Optional.of(project(null, ProjectStatus.ACTIVE)));

        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID().toString(), pid.toString()))
                .isInstanceOf(ProjectException.class);

        verify(projectRefundService, never()).refundOnCancellation(org.mockito.ArgumentMatchers.any());
    }
}
