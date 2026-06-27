package com.example.smartmoney.application.dtos.responses;

import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.valueobjects.Email;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("UserResponse onboarding flags")
class UserResponseTest {

    @Test
    @DisplayName("New user response has onboarding flags false")
    void newUserFlagsAreFalse() {
        User user = baseUser();

        UserResponse response = UserResponse.fromDomain(user);

        assertFalse(response.isIncomeSetupCompleted());
        assertFalse(response.isFinancialSetupCompleted());
        assertFalse(response.isOnboardingCompleted());
    }

    @Test
    @DisplayName("Onboarding is completed only when income and financial setup are complete")
    void onboardingCompletedRequiresBothFlags() {
        User user = baseUser();
        user.setIncomeSetupCompleted(true);
        user.setFinancialSetupCompleted(false);

        UserResponse incomeOnly = UserResponse.fromDomain(user);
        assertTrue(incomeOnly.isIncomeSetupCompleted());
        assertFalse(incomeOnly.isFinancialSetupCompleted());
        assertFalse(incomeOnly.isOnboardingCompleted());

        user.setFinancialSetupCompleted(true);

        UserResponse completed = UserResponse.fromDomain(user);
        assertTrue(completed.isIncomeSetupCompleted());
        assertTrue(completed.isFinancialSetupCompleted());
        assertTrue(completed.isOnboardingCompleted());
    }

    private User baseUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .username("user")
                .fullName("User")
                .email(new Email("user@example.com"))
                .active(true)
                .build();
    }
}
