package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.dtos.requests.auth.RefreshTokenRequest;
import com.example.smartmoney.application.dtos.responses.AuthResponse;
import com.example.smartmoney.application.dtos.responses.UserResponse;
import com.example.smartmoney.domain.exceptions.InvalidRefreshTokenException;
import com.example.smartmoney.application.ports.auth.AuthPort;
import com.example.smartmoney.domain.models.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class RefreshTokenUseCase {
    
    private final AuthPort authPort;
    
    public AuthResponse execute(RefreshTokenRequest request) {
        // Validate refresh token và lấy user
        User user = authPort.validateRefreshToken(request.getRefreshToken());
        
        if (user == null) {
             throw new InvalidRefreshTokenException();
        }
        
        // Check if user is active
        if (!user.isActive()) {
            throw new com.example.smartmoney.domain.exceptions.AuthenticationException(
                "Account is deactivated"
            );
        }
        
        // Generate new tokens — rotate the refresh token so the old one is immediately invalidated
        String newAccessToken = authPort.generateAccessToken(user);
        String newRefreshToken = authPort.generateRefreshToken(user);
        
        // Convert to response
        UserResponse userResponse = mapToUserResponse(user);
        
        return new AuthResponse(
            newAccessToken,
            newRefreshToken,
            "Bearer",
            3600L, // 1 hour in seconds
            userResponse
        );
    }

    private UserResponse mapToUserResponse(User user) {
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
        // Disabled: income/financial setup flags removed from User (user_income / user_financial_profile tables removed).
        // response.setIncomeSetupCompleted(user.isIncomeSetupCompleted());
        // response.setFinancialSetupCompleted(user.isFinancialSetupCompleted());
        // response.setOnboardingCompleted(user.isIncomeSetupCompleted() && user.isFinancialSetupCompleted());
        return response;
    }

}
