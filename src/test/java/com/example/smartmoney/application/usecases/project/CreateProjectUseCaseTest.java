package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.requests.project.CreateProjectRequest;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
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
@DisplayName("CreateProjectUseCase validation rules")
class CreateProjectUseCaseTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserIncomeRepository userIncomeRepository;

    @Mock
    private ProjectTrackingRepository projectTrackingRepository;

    @Mock
    private SafeSpendingService safeSpendingService;

    private CreateProjectUseCase useCase;

    private UUID actorUuid;
    private String actorId;

    @BeforeEach
    void setUp() {
        useCase = new CreateProjectUseCase(projectRepository, projectTrackingRepository, userIncomeRepository, safeSpendingService);
        actorUuid = UUID.randomUUID();
        actorId = actorUuid.toString();
    }

    @Test
    @DisplayName("Reject when user income row missing")
    void rejectWhenUserIncomeMissing() {
        CreateProjectRequest req = baseReq();

        when(userIncomeRepository.findById(eq(actorUuid))).thenReturn(Optional.empty());

        ProjectException ex = assertThrows(ProjectException.class, () -> useCase.execute(actorId, req));
        assertEquals(ProjectErrorCode.PROJECT_USER_INCOME_REQUIRED.name(), ex.getErrorCode());
    }

    @Test
    @DisplayName("Reject when already 3 ACTIVE projects")
    void rejectWhenMaxActiveReached() {
        CreateProjectRequest req = baseReq();

        when(userIncomeRepository.findById(eq(actorUuid))).thenReturn(Optional.of(validIncome(actorUuid)));
        when(projectRepository.findAllByUser(eq(UserId.of(actorId)))).thenReturn(List.of(
                activeProject(ProjectPriority.LOW, LocalDate.now()),
                activeProject(ProjectPriority.MEDIUM, LocalDate.now()),
                activeProject(ProjectPriority.HIGH, LocalDate.now())
        ));

        ProjectException ex = assertThrows(ProjectException.class, () -> useCase.execute(actorId, req));
        assertEquals(ProjectErrorCode.PROJECT_MAX_ACTIVE_REACHED.name(), ex.getErrorCode());
    }

    @Test
    @DisplayName("Reject when new priority duplicates an ACTIVE project's priority")
    void rejectWhenPriorityConflict() {
        CreateProjectRequest req = baseReq();
        req.setPriority(ProjectPriority.HIGH);

        when(userIncomeRepository.findById(eq(actorUuid))).thenReturn(Optional.of(validIncome(actorUuid)));
        when(projectRepository.findAllByUser(eq(UserId.of(actorId)))).thenReturn(List.of(
                activeProject(ProjectPriority.HIGH, LocalDate.now())
        ));

        ProjectException ex = assertThrows(ProjectException.class, () -> useCase.execute(actorId, req));
        assertEquals(ProjectErrorCode.PROJECT_ACTIVE_PRIORITY_CONFLICT.name(), ex.getErrorCode());
    }

    @Test
    @DisplayName("Reject when monthly sum exceeds 20% usable_income")
    void rejectWhenMonthlySumExceedsLimit() {
        CreateProjectRequest req = baseReq();
        // new project monthly = 100 (deadline today => 1 month)
        req.setTargetAmount(new BigDecimal("100"));
        req.setDeadline(LocalDate.now());

        UserIncome income = validIncome(actorUuid);
        // usable_income = 1000 => limit = 200

        when(userIncomeRepository.findById(eq(actorUuid))).thenReturn(Optional.of(income));
        when(projectRepository.findAllByUser(eq(UserId.of(actorId)))).thenReturn(List.of(
                // existing monthly = 150
                Project.createNew(UserId.of(actorId), ProjectType.PERSONAL, ProjectPriority.LOW,
                        "P1", null, new Money(new BigDecimal("150"), "VND"), LocalDate.now())
        ));

        ProjectException ex = assertThrows(ProjectException.class, () -> useCase.execute(actorId, req));
        assertEquals(ProjectErrorCode.PROJECT_USABLE_INCOME_LIMIT_EXCEEDED.name(), ex.getErrorCode());
    }

    @Test
    @DisplayName("Reject when deadline exceeds 60 months")
    void rejectWhenDeadlineTooFar() {
        CreateProjectRequest req = baseReq();
        req.setDeadline(LocalDate.now().plusMonths(60));

        when(userIncomeRepository.findById(eq(actorUuid))).thenReturn(Optional.of(validIncome(actorUuid)));
        when(projectRepository.findAllByUser(eq(UserId.of(actorId)))).thenReturn(List.of());

        ProjectException ex = assertThrows(ProjectException.class, () -> useCase.execute(actorId, req));
        assertEquals(ProjectErrorCode.PROJECT_INVALID_DEADLINE.name(), ex.getErrorCode());
    }

    private CreateProjectRequest baseReq() {
        CreateProjectRequest req = new CreateProjectRequest();
        req.setName("My Project");
        req.setDescription(null);
        req.setType(ProjectType.PERSONAL);
        req.setPriority(ProjectPriority.MEDIUM);
        req.setTargetAmount(new BigDecimal("1000"));
        req.setCurrency("VND");
        req.setDeadline(LocalDate.now());
        return req;
    }

    private UserIncome validIncome(UUID userId) {
        return UserIncome.createNew(userId, 2000, 1000, "VND", null, null);
    }

    private Project activeProject(ProjectPriority priority, LocalDate deadline) {
        return Project.createNew(UserId.of(actorId), ProjectType.PERSONAL, priority,
                "P", null, new Money(new BigDecimal("10"), "VND"), deadline);
    }
}
