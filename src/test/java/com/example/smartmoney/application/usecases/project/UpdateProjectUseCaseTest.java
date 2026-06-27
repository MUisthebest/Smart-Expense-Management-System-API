package com.example.smartmoney.application.usecases.project;

/*
import com.example.smartmoney.application.dtos.requests.project.UpdateProjectRequest;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.user.UserIncome;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectTrackingRepository;
import com.example.smartmoney.domain.repositories.UserIncomeRepository;
import com.example.smartmoney.domain.services.SafeSpendingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UpdateProjectUseCase validation rules")
class UpdateProjectUseCaseTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserIncomeRepository userIncomeRepository;

    @Mock
    private ProjectTrackingRepository projectTrackingRepository;

    @Mock
    private SafeSpendingService safeSpendingService;

    private UpdateProjectUseCase useCase;

    private UUID actorUuid;
    private String actorId;

    @BeforeEach
    void setUp() {
        useCase = new UpdateProjectUseCase(projectRepository, projectTrackingRepository, userIncomeRepository, safeSpendingService);
        actorUuid = UUID.randomUUID();
        actorId = actorUuid.toString();
    }

    @Test
    @DisplayName("Reject update when user income row missing")
    void rejectWhenUserIncomeMissing() {
        Project existing = activeProject(ProjectPriority.MEDIUM, LocalDate.now());
        UpdateProjectRequest req = new UpdateProjectRequest();
        req.setName("New");

        when(projectRepository.findById(eq(ProjectId.of(existing.getId().toString())))).thenReturn(Optional.of(existing));
        when(userIncomeRepository.findByUserId(eq(actorUuid))).thenReturn(Optional.empty());

        ProjectException ex = assertThrows(ProjectException.class,
                () -> useCase.execute(actorId, existing.getId().toString(), req));
        assertEquals(ProjectErrorCode.PROJECT_USER_INCOME_REQUIRED.name(), ex.getErrorCode());
    }

    @Test
    @DisplayName("Reject when setting ACTIVE and already 3 other ACTIVE projects")
    void rejectWhenMaxActiveReached() {
        Project existing = completedProject(ProjectPriority.LOW, LocalDate.now());

        UpdateProjectRequest req = new UpdateProjectRequest();
        req.setStatus(ProjectStatus.ACTIVE);

        when(projectRepository.findById(any())).thenReturn(Optional.of(existing));
        when(userIncomeRepository.findByUserId(eq(actorUuid))).thenReturn(Optional.of(validIncome(actorUuid)));

        when(projectRepository.findAllByUser(eq(UserId.of(actorId)))).thenReturn(List.of(
                activeProject(ProjectPriority.LOW, LocalDate.now()),
                activeProject(ProjectPriority.MEDIUM, LocalDate.now()),
                activeProject(ProjectPriority.HIGH, LocalDate.now()),
                existing
        ));

        ProjectException ex = assertThrows(ProjectException.class,
                () -> useCase.execute(actorId, existing.getId().toString(), req));
        assertEquals(ProjectErrorCode.PROJECT_MAX_ACTIVE_REACHED.name(), ex.getErrorCode());
    }

    @Test
    @DisplayName("Reject when updating priority conflicts with another ACTIVE project")
    void rejectWhenPriorityConflict() {
        Project existing = activeProject(ProjectPriority.LOW, LocalDate.now());

        UpdateProjectRequest req = new UpdateProjectRequest();
        req.setPriority(ProjectPriority.HIGH);

        when(projectRepository.findById(any())).thenReturn(Optional.of(existing));
        when(userIncomeRepository.findByUserId(eq(actorUuid))).thenReturn(Optional.of(validIncome(actorUuid)));
        when(projectRepository.findAllByUser(eq(UserId.of(actorId)))).thenReturn(List.of(
                existing,
                activeProject(ProjectPriority.HIGH, LocalDate.now())
        ));

        ProjectException ex = assertThrows(ProjectException.class,
                () -> useCase.execute(actorId, existing.getId().toString(), req));
        assertEquals(ProjectErrorCode.PROJECT_ACTIVE_PRIORITY_CONFLICT.name(), ex.getErrorCode());
    }

    @Test
    @DisplayName("Reject when updating target/deadline causes monthly sum to exceed limit")
    void rejectWhenMonthlySumExceedsLimit() {
        Project existing = activeProject(ProjectPriority.MEDIUM, LocalDate.now());

        UpdateProjectRequest req = new UpdateProjectRequest();
        req.setTargetAmount(new BigDecimal("200"));
        req.setDeadline(LocalDate.now());

        when(projectRepository.findById(any())).thenReturn(Optional.of(existing));
        when(userIncomeRepository.findByUserId(eq(actorUuid))).thenReturn(Optional.of(validIncome(actorUuid)));

        when(projectRepository.findAllByUser(eq(UserId.of(actorId)))).thenReturn(List.of(
                existing,
                // monthly = 150
                Project.createNew(UserId.of(actorId), ProjectType.PERSONAL, ProjectPriority.LOW,
                        "P1", null, new Money(new BigDecimal("150"), "VND"), LocalDate.now())
        ));

        ProjectException ex = assertThrows(ProjectException.class,
                () -> useCase.execute(actorId, existing.getId().toString(), req));
        assertEquals(ProjectErrorCode.PROJECT_USABLE_INCOME_LIMIT_EXCEEDED.name(), ex.getErrorCode());
    }

    @Test
    @DisplayName("Reject when deadline exceeds 60 months")
    void rejectWhenDeadlineTooFar() {
        Project existing = activeProject(ProjectPriority.MEDIUM, LocalDate.now());

        UpdateProjectRequest req = new UpdateProjectRequest();
        req.setDeadline(LocalDate.now().plusMonths(60));

        when(projectRepository.findById(any())).thenReturn(Optional.of(existing));
        when(userIncomeRepository.findByUserId(eq(actorUuid))).thenReturn(Optional.of(validIncome(actorUuid)));
        when(projectRepository.findAllByUser(eq(UserId.of(actorId)))).thenReturn(List.of(existing));

        ProjectException ex = assertThrows(ProjectException.class,
                () -> useCase.execute(actorId, existing.getId().toString(), req));
        assertEquals(ProjectErrorCode.PROJECT_INVALID_DEADLINE.name(), ex.getErrorCode());
    }

    private UserIncome validIncome(UUID userId) {
        return UserIncome.createNew(userId, 2000, 1000, "VND", null, null);
    }

    private Project activeProject(ProjectPriority priority, LocalDate deadline) {
        Project p = Project.createNew(UserId.of(actorId), ProjectType.PERSONAL, priority,
                "P", null, new Money(new BigDecimal("10"), "VND"), deadline);
        p.updateStatus(UserId.of(actorId), ProjectStatus.ACTIVE);
        return p;
    }

    private Project completedProject(ProjectPriority priority, LocalDate deadline) {
        Project p = Project.createNew(UserId.of(actorId), ProjectType.PERSONAL, priority,
                "P", null, new Money(new BigDecimal("10"), "VND"), deadline);
        p.updateStatus(UserId.of(actorId), ProjectStatus.COMPLETED);
        return p;
    }
}
*/
