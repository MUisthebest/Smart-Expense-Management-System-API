package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.requests.project.ProjectBudgetAdvisorRequest;
import com.example.smartmoney.application.dtos.responses.project.ProjectBudgetAdvisorResponse;
import com.example.smartmoney.application.ports.llm.LlmPort;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Focus_Mode;
import com.example.smartmoney.domain.models.user.enums.Intervention_Level;
import com.example.smartmoney.domain.models.user.enums.Saving_Pace;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectBudgetAdvisorUseCase mode ordering")
class ProjectBudgetAdvisorUseCaseTest {

    private static final int MAX_MONTHS = 60;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private LlmPort llmPort;

    private ProjectBudgetAdvisorUseCase useCase;
    private UUID userId;
    private String actorId;

    @BeforeEach
    void setUp() {
        useCase = new ProjectBudgetAdvisorUseCase(userRepository, projectRepository, llmPort);
        userId = UUID.randomUUID();
        actorId = userId.toString();
    }

    @Test
    @DisplayName("URGENT never stretches longer than RELAXED when existing projects squeeze the budget")
    void urgentNeverLongerThanRelaxedWhenBudgetSqueezed() {
        // income 25M x 20% (BALANCED) = 5,000,000 capacity.
        // A MEDIUM and a LOW project due this month consume 4,900,000, leaving 100,000/month.
        stubUser();
        stubActiveProjects(
                project(ProjectPriority.MEDIUM, "3000000"),
                project(ProjectPriority.LOW, "1900000")
        );

        ProjectBudgetAdvisorResponse urgent = useCase.execute(actorId, request(Mode.URGENT, "10000000"));
        ProjectBudgetAdvisorResponse relaxed = useCase.execute(actorId, request(Mode.RELAXED, "10000000"));

        assertTrue(
                urgent.getNumberOfMonths() <= relaxed.getNumberOfMonths(),
                "URGENT (" + urgent.getNumberOfMonths() + "m) must not exceed RELAXED ("
                        + relaxed.getNumberOfMonths() + "m)"
        );
        // Both raw plans overshoot the ceiling here (100m and 200m), so both clamp to it.
        // Before the fix only RELAXED was clamped, so URGENT came back at 100 months.
        assertEquals(MAX_MONTHS, urgent.getNumberOfMonths());
        assertEquals(MAX_MONTHS, relaxed.getNumberOfMonths());
    }

    @Test
    @DisplayName("Below the MAX_MONTHS ceiling RELAXED takes twice as long as URGENT")
    void relaxedIsTwiceUrgentBelowCeiling() {
        stubUser();
        stubActiveProjects();

        // Full 5,000,000/month capacity, target 10,000,000 -> URGENT 2 months, RELAXED 4 months.
        ProjectBudgetAdvisorResponse urgent = useCase.execute(actorId, request(Mode.URGENT, "10000000"));
        ProjectBudgetAdvisorResponse relaxed = useCase.execute(actorId, request(Mode.RELAXED, "10000000"));

        assertEquals(2, urgent.getNumberOfMonths());
        assertEquals(4, relaxed.getNumberOfMonths());
        assertTrue(urgent.getNumberOfMonths() <= relaxed.getNumberOfMonths());
    }

    private void stubUser() {
        when(userRepository.findById(eq(userId))).thenReturn(Optional.of(
                User.builder()
                        .id(userId)
                        .username("user")
                        .fullName("User")
                        .active(true)
                        .income(new BigDecimal("25000000"))
                        .savingPace(Saving_Pace.BALANCED)
                        .focusMode(Focus_Mode.REDUCE_SPENDING)
                        .interventionLevel(Intervention_Level.NOTIFY)
                        .autoInvestSurplus(Boolean.TRUE)
                        .build()
        ));
    }

    private void stubActiveProjects(Project... projects) {
        when(projectRepository.findAllByUser(any(UserId.class))).thenReturn(List.of(projects));
    }

    private Project project(ProjectPriority priority, String target) {
        return Project.createNew(
                UserId.of(actorId),
                ProjectType.PERSONAL,
                priority,
                priority.name() + " project",
                null,
                new Money(new BigDecimal(target), "VND"),
                LocalDate.now() // due this month -> 1 month of runway
        );
    }

    private ProjectBudgetAdvisorRequest request(Mode mode, String target) {
        ProjectBudgetAdvisorRequest req = new ProjectBudgetAdvisorRequest();
        req.setName("New project");
        req.setType(ProjectType.PERSONAL);
        req.setTargetAmount(new BigDecimal(target));
        req.setCurrency("VND");
        req.setDeadline(LocalDate.now().plusMonths(6));
        req.setMode(mode == Mode.URGENT
                ? ProjectBudgetAdvisorRequest.Mode.URGENT
                : ProjectBudgetAdvisorRequest.Mode.RELAXED);
        return req;
    }

    private enum Mode { URGENT, RELAXED }
}
