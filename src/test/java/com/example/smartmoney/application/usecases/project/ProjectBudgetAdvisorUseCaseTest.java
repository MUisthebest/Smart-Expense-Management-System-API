package com.example.smartmoney.application.usecases.project;

// Disabled: tests removed UserIncomeRepository / ProjectBudgetAdvisorUseCase API (user_income table removed).
/*
import com.example.smartmoney.application.dtos.requests.project.ProjectBudgetAdvisorRequest;
import com.example.smartmoney.application.dtos.responses.project.ProjectBudgetAdvisorResponse;
import com.example.smartmoney.application.ports.llm.LlmPort;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.UserIncome;
import com.example.smartmoney.domain.repositories.UserIncomeRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectBudgetAdvisorUseCase onboarding validation")
class ProjectBudgetAdvisorUseCaseTest {

    @Mock
    private UserIncomeRepository userIncomeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LlmPort llmPort;

    private ProjectBudgetAdvisorUseCase useCase;
    private UUID userId;
    private String actorId;

    @BeforeEach
    void setUp() {
        useCase = new ProjectBudgetAdvisorUseCase(userIncomeRepository, userRepository, llmPort);
        userId = UUID.randomUUID();
        actorId = userId.toString();
    }

    @Test
    @DisplayName("Reject when income setup is missing")
    void rejectWhenIncomeSetupMissing() {
        User user = user(false, true);
        when(userRepository.findById(eq(userId))).thenReturn(Optional.of(user));

        ProjectException ex = assertThrows(ProjectException.class, () -> useCase.execute(actorId, request()));

        assertEquals(ProjectErrorCode.INCOME_PROFILE_REQUIRED.name(), ex.getErrorCode());
    }

    @Test
    @DisplayName("Reject when financial setup is missing")
    void rejectWhenFinancialSetupMissing() {
        User user = user(true, false);
        when(userRepository.findById(eq(userId))).thenReturn(Optional.of(user));

        ProjectException ex = assertThrows(ProjectException.class, () -> useCase.execute(actorId, request()));

        assertEquals(ProjectErrorCode.FINANCIAL_PROFILE_REQUIRED.name(), ex.getErrorCode());
    }

    @Test
    @DisplayName("Runs when both setup flags are complete")
    void runsWhenBothFlagsComplete() {
        User user = user(true, true);
        when(userRepository.findById(eq(userId))).thenReturn(Optional.of(user));
        when(userIncomeRepository.findByUserId(eq(userId)))
                .thenReturn(Optional.of(UserIncome.createNew(userId, 2_000, 1_000, "VND", null, null)));

        ProjectBudgetAdvisorResponse response = useCase.execute(actorId, request());

        assertNotNull(response);
        assertEquals(new BigDecimal("200.00"), response.getMonthlySaving());
        assertEquals(5, response.getNumberOfMonths());
    }

    private ProjectBudgetAdvisorRequest request() {
        ProjectBudgetAdvisorRequest req = new ProjectBudgetAdvisorRequest();
        req.setName("Project");
        req.setType(ProjectType.PERSONAL);
        req.setTargetAmount(new BigDecimal("1000"));
        req.setCurrency("VND");
        req.setDeadline(LocalDate.now());
        req.setMode(ProjectBudgetAdvisorRequest.Mode.URGENT);
        return req;
    }

    private User user(boolean incomeSetupCompleted, boolean financialSetupCompleted) {
        return User.builder()
                .id(userId)
                .username("user")
                .fullName("User")
                .active(true)
                .incomeSetupCompleted(incomeSetupCompleted)
                .financialSetupCompleted(financialSetupCompleted)
                .build();
    }
}
*/
