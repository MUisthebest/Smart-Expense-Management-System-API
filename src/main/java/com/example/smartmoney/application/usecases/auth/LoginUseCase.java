package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.dtos.requests.auth.LoginRequest;
import com.example.smartmoney.application.dtos.responses.AuthResponse;
import com.example.smartmoney.application.dtos.responses.UserResponse;
import com.example.smartmoney.application.ports.auth.AuthPort;
import com.example.smartmoney.domain.models.user.*;
import com.example.smartmoney.domain.models.user.valueobjects.*;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.exceptions.EmailNotVerifiedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LoginUseCase {
    
    private final UserRepository userRepository;
    private final AuthPort authPort;
    
    public LoginUseCase(UserRepository userRepository, AuthPort authPort) {
        this.userRepository = userRepository;
        this.authPort = authPort;
    }
    
    public AuthResponse execute(LoginRequest request) {
        Email email = new Email(request.getEmail());

        // Find user by email
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new DomainException("Invalid email"));

        // Check if email is verified
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }
        
        // Validate password
        if (!authPort.validatePassword(request.getPassword(), user.getPassword().getHash())) {
            throw new DomainException("Invalid password");
        }
        
        // Check if user is active
        if (!user.isActive()) {
            throw new DomainException("Account is deactivated");
        }
        
        // Generate tokens
        String accessToken = authPort.generateAccessToken(user);
        String refreshToken = authPort.generateRefreshToken(user);


        // Convert to response
        UserResponse userResponse = mapToUserResponse(user);
        
        return new AuthResponse(
            accessToken,
            refreshToken,
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
