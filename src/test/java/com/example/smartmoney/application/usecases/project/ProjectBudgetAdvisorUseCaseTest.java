package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.requests.project.ProjectBudgetAdvisorRequest;
import com.example.smartmoney.application.dtos.responses.project.ProjectBudgetAdvisorResponse;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Saving_Pace;
import com.example.smartmoney.domain.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectBudgetAdvisorUseCase — pace from trait, advisory only")
class ProjectBudgetAdvisorUseCaseTest {

    @Mock
    private UserRepository userRepository;

    private ProjectBudgetAdvisorUseCase useCase;

    private String actorId;

    @BeforeEach
    void setUp() {
        useCase = new ProjectBudgetAdvisorUseCase(userRepository);
        actorId = UUID.randomUUID().toString();
    }

    private ProjectBudgetAdvisorRequest req() {
        ProjectBudgetAdvisorRequest r = new ProjectBudgetAdvisorRequest();
        r.setName("Vacation");
        r.setType(ProjectType.PERSONAL);
        r.setTargetAmount(new BigDecimal("12000"));
        r.setCurrency("VND");
        r.setDeadline(LocalDate.now().plusMonths(12));
        return r;
    }

    private void userWithPace(Saving_Pace pace) {
        User u = User.builder().savingPace(pace).build();
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(u));
    }

    @Test
    @DisplayName("BALANCED spreads evenly across the deadline horizon")
    void balancedTracksDeadline() {
        userWithPace(Saving_Pace.BALANCED);

        ProjectBudgetAdvisorResponse res = useCase.execute(actorId, req());

        assertEquals(12, res.getNumberOfMonths());
        assertEquals(0, res.getMonthlySaving().compareTo(new BigDecimal("1000.00")));
    }

    @Test
    @DisplayName("AGGRESIVE finishes sooner with a higher monthly saving")
    void aggressiveFinishesSooner() {
        userWithPace(Saving_Pace.AGGRESIVE);

        ProjectBudgetAdvisorResponse res = useCase.execute(actorId, req());

        assertEquals(9, res.getNumberOfMonths());          // ceil(12 * 0.75)
        assertTrue(res.getMonthlySaving().compareTo(new BigDecimal("1000.00")) > 0);
    }

    @Test
    @DisplayName("RELAXED stretches one month past the deadline with a lower monthly saving")
    void relaxedStretches() {
        userWithPace(Saving_Pace.RELAXED);

        ProjectBudgetAdvisorResponse res = useCase.execute(actorId, req());

        assertEquals(13, res.getNumberOfMonths());
        assertTrue(res.getMonthlySaving().compareTo(new BigDecimal("1000.00")) < 0);
    }

    @Test
    @DisplayName("Defaults to BALANCED when the user has no trait set (cold start) — never throws")
    void defaultsWhenNoTrait() {
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        ProjectBudgetAdvisorResponse res = useCase.execute(actorId, req());

        assertEquals(12, res.getNumberOfMonths());
        assertEquals(0, res.getMonthlySaving().compareTo(new BigDecimal("1000.00")));
    }
}
