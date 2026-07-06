package com.example.smartmoney.application.dtos.responses;

import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Role;
import lombok.Data;
import lombok.*;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private UUID id;
    private String username;
    private String fullName;
    private String avatar;
    private String dateOfBirth;
    private String phone;
    private String email;
    private Integer coin;
    private Role role;
    private Double rate;
    private boolean active;

    private boolean financialSetupCompleted;

//    private boolean incomeSetupCompleted;
//    private boolean financialSetupCompleted;
//    private boolean onboardingCompleted;

    public static UserResponse fromDomain(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setFullName(user.getFullName());
        response.setAvatar(user.getAvatar() != null ? user.getAvatar().getUrl() : null);
        response.setDateOfBirth(user.getDateOfBirth() != null ? user.getDateOfBirth().getFormattedValue() : null);
        response.setPhone(user.getPhone() != null ? user.getPhone().getValue() : null);
        response.setEmail(user.getEmail() != null ? user.getEmail().getValue() : null);
        response.setCoin(user.getCoin());
        response.setRole(user.getRole());
        response.setRate(user.getRate());
        response.setActive(user.isActive());

        response.setFinancialSetupCompleted(
                user.hasCompletedFinancialSetup()
        );

        // Disabled: income/financial setup flags removed from User (user_income / user_financial_profile tables removed).
        // response.setIncomeSetupCompleted(user.isIncomeSetupCompleted());
        // response.setFinancialSetupCompleted(user.isFinancialSetupCompleted());
        // response.setOnboardingCompleted(user.isIncomeSetupCompleted() && user.isFinancialSetupCompleted());
        return response;
    }
}
