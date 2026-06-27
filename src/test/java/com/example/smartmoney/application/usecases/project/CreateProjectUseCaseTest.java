package com.example.smartmoney.application.usecases.project;

/*
import com.example.smartmoney.application.dtos.requests.project.CreateProjectRequest;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.UserIncome;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectTrackingRepository;
import com.example.smartmoney.domain.repositories.UserIncomeRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.services.SafeSpendingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import static org.mockito.ArgumentMatchers.any;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
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
    private UserRepository userRepository;

    @Mock
    private ProjectTrackingRepository projectTrackingRepository;

    @Mock
    private SafeSpendingService safeSpendingService;

    private CreateProjectUseCase useCase;

    private UUID actorUuid;
    private String actorId;

    /** A fixed date whose dayOfMonth is 1, inside the V13 creation window (days 1-7). */
    private static final LocalDate WINDOW_DATE = LocalDate.of(2026, 6, 1);

    @BeforeEach
    void setUp() {
        useCase = new CreateProjectUseCase(projectRepository, projectTrackingRepository, userIncomeRepository, userRepository, safeSpendingService);
        actorUuid = UUID.randomUUID();
        actorId = actorUuid.toString();
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(onboardedUser()));
    }

    @Test
    @DisplayName("Reject when user income row missing")
    void rejectWhenUserIncomeMissing() {
        try (MockedStatic<LocalDate> mocked = mockLocalDate()) {
            CreateProjectRequest req = baseReq();
            when(userIncomeRepository.findByUserId(eq(actorUuid))).thenReturn(Optional.empty());

            ProjectException ex = assertThrows(ProjectException.class, () -> useCase.execute(actorId, req));
            assertEquals(ProjectErrorCode.PROJECT_USER_INCOME_REQUIRED.name(), ex.getErrorCode());
        }
    }
  
    @Test
    @DisplayName("Reject when already 3 ACTIVE projects")
    void rejectWhenMaxActiveReached() {
        try (MockedStatic<LocalDate> mocked = mockLocalDate()) {
            CreateProjectRequest req = baseReq();

            Project p1 = activeProject(ProjectPriority.LOW, WINDOW_DATE);
            Project p2 = activeProject(ProjectPriority.MEDIUM, WINDOW_DATE);
            Project p3 = activeProject(ProjectPriority.HIGH, WINDOW_DATE);

            when(userIncomeRepository.findByUserId(eq(actorUuid))).thenReturn(Optional.of(validIncome(actorUuid)));
            when(projectRepository.findAllByUser(eq(UserId.of(actorId)))).thenReturn(List.of(p1, p2, p3));

            ProjectException ex = assertThrows(ProjectException.class, () -> useCase.execute(actorId, req));
            assertEquals(ProjectErrorCode.PROJECT_MAX_ACTIVE_REACHED.name(), ex.getErrorCode());
        }
    } 
  
    @Test
    @DisplayName("Reject when new priority duplicates an ACTIVE project's priority")
    void rejectWhenPriorityConflict() {
        try (MockedStatic<LocalDate> mocked = mockLocalDate()) {
            CreateProjectRequest req = baseReq();
            req.setPriority(ProjectPriority.HIGH);

            Project existing = activeProject(ProjectPriority.HIGH, WINDOW_DATE);


            when(userIncomeRepository.findByUserId(eq(actorUuid))).thenReturn(Optional.of(validIncome(actorUuid)));
            when(projectRepository.findAllByUser(eq(UserId.of(actorId)))).thenReturn(List.of(existing));

            ProjectException ex = assertThrows(ProjectException.class, () -> useCase.execute(actorId, req));
            assertEquals(ProjectErrorCode.PROJECT_ACTIVE_PRIORITY_CONFLICT.name(), ex.getErrorCode());
        }
    }
  
    @Test
    @DisplayName("Reject when monthly sum exceeds 20% usable_income")
    void rejectWhenMonthlySumExceedsLimit() {
        try (MockedStatic<LocalDate> mocked = mockLocalDate()) {
            CreateProjectRequest req = baseReq();
            req.setTargetAmount(new BigDecimal("100"));
            req.setDeadline(WINDOW_DATE);

            UserIncome income = validIncome(actorUuid);

            Project existing = Project.createNew(
                    UserId.of(actorId),
                    ProjectType.PERSONAL,
                    ProjectPriority.LOW,
                    "P1",
                    null,
                    new Money(new BigDecimal("150"), "VND"),
                    WINDOW_DATE
            );

            when(userIncomeRepository.findByUserId(eq(actorUuid))).thenReturn(Optional.of(income));
            when(projectRepository.findAllByUser(eq(UserId.of(actorId)))).thenReturn(List.of(existing));

            ProjectException ex = assertThrows(ProjectException.class, () -> useCase.execute(actorId, req));
            assertEquals(ProjectErrorCode.PROJECT_USABLE_INCOME_LIMIT_EXCEEDED.name(), ex.getErrorCode());
        }
    }

    @Test
    @DisplayName("Reject when deadline exceeds 60 months")
    void rejectWhenDeadlineTooFar() {
        try (MockedStatic<LocalDate> mocked = mockLocalDate()) {
            CreateProjectRequest req = baseReq();
            req.setDeadline(WINDOW_DATE.plusMonths(60));

            when(userIncomeRepository.findByUserId(eq(actorUuid))).thenReturn(Optional.of(validIncome(actorUuid)));
            when(projectRepository.findAllByUser(eq(UserId.of(actorId)))).thenReturn(List.of());

            ProjectException ex = assertThrows(ProjectException.class, () -> useCase.execute(actorId, req));
            assertEquals(ProjectErrorCode.PROJECT_INVALID_DEADLINE.name(), ex.getErrorCode());
        }
    }

    /**
     * Pins {@link LocalDate#now()} to {@link #WINDOW_DATE} (day-of-month 1, inside the
     * V13 days 1-7 creation window) so the date-gate check passes, while leaving every
     * other LocalDate method (of, plusMonths, ...) real.
     */
    private MockedStatic<LocalDate> mockLocalDate() {
        MockedStatic<LocalDate> mocked = mockStatic(LocalDate.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        mocked.when(LocalDate::now).thenReturn(WINDOW_DATE);
        return mocked;
    }

    private CreateProjectRequest baseReq() {
        CreateProjectRequest req = new CreateProjectRequest();
        req.setName("My Project");
        req.setDescription(null);
        req.setPriority(ProjectPriority.MEDIUM);
        req.setTargetAmount(new BigDecimal("1000"));
        req.setCurrency("VND");
        req.setDeadline(WINDOW_DATE);
        return req;
    }

    private UserIncome validIncome(UUID userId) {
        return UserIncome.createNew(userId, 2000, 1000, "VND", null, null);
    }

    private Project activeProject(ProjectPriority priority, LocalDate deadline) {
        return Project.createNew(UserId.of(actorId), ProjectType.PERSONAL, priority,
                "P", null, new Money(new BigDecimal("10"), "VND"), deadline);
    }

    private User onboardedUser() {
        return User.builder()
                .id(actorUuid)
                .username("user")
                .fullName("User")
                .active(true)
                .incomeSetupCompleted(true)
                .financialSetupCompleted(true)
                .build();
    }
}
*/
