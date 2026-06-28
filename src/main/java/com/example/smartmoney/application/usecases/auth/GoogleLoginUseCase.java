package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.dtos.requests.auth.GoogleLoginRequest;
import com.example.smartmoney.application.dtos.responses.AuthResponse;
import com.example.smartmoney.application.dtos.responses.UserResponse;
import com.example.smartmoney.application.ports.auth.AuthPort;
import com.example.smartmoney.application.ports.auth.ExternalOAuthPort;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.valueobjects.Avatar;
import com.example.smartmoney.domain.models.user.valueobjects.Email;
import com.example.smartmoney.domain.models.user.valueobjects.RefreshToken;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.application.dtos.requests.auth.ExternalOAuthUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class GoogleLoginUseCase {
    
    private static final Logger logger = LoggerFactory.getLogger(GoogleLoginUseCase.class);
    
    private final UserRepository userRepository;
    private final AuthPort authPort;
    private final ExternalOAuthPort externalOAuthPort;

    public GoogleLoginUseCase(
            UserRepository userRepository, 
            AuthPort authPort,
            ExternalOAuthPort externalOAuthPort
            ) {
        this.userRepository = userRepository;
        this.authPort = authPort;
        this.externalOAuthPort = externalOAuthPort;
    }
    
    public AuthResponse execute(GoogleLoginRequest request) {
        try {
            // Verify Google ID Token
            ExternalOAuthUser oauthUser  = externalOAuthPort.verifyGoogleToken(request.getIdToken());
            if (oauthUser == null || oauthUser.getEmail() == null) {
                throw new DomainException("Invalid Google token");
            }
            
            Email email = new Email(oauthUser.getEmail());
            
            User user = userRepository.findByEmail(email)
                    .orElseGet(() -> createUser(oauthUser));

            if (!user.isEmailVerified()) {
                user.setEmailVerified(true);
            }
            String accessToken = authPort.generateAccessToken(user);
            String refreshToken = authPort.generateRefreshToken(user);

            user.updateRefreshToken(
                    new RefreshToken(refreshToken, LocalDateTime.now().plusDays(7))
            );
            userRepository.save(user);

            return new AuthResponse(
                    accessToken,
                    refreshToken,
                    "Bearer",
                    externalOAuthPort.getGoogleTokenValiditySeconds(),
                    mapUserToResponse(user)
            );
        } catch (Exception e) {
            logger.error("Google login verification failed", e);
            throw new DomainException("Google authentication failed: " + e.getMessage());
        }
    }
    
    private User createUser(ExternalOAuthUser oauthUser) {
        Avatar avatar = oauthUser.getAvatar() != null ? new Avatar(oauthUser.getAvatar()) : null;
        return userRepository.save(
                User.createGoogleUser(
                        generateUsername(oauthUser.getEmail()),
                        oauthUser.getEmail(),
                        oauthUser.getName(),
                        avatar
                )
        );
    }

    private String generateUsername(String email) {
        return email.split("@")[0] + "_" + UUID.randomUUID().toString().substring(0, 6);
    }

    
    private UserResponse mapUserToResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setFullName(user.getFullName());
        response.setEmail(user.getEmail() != null ? user.getEmail().getValue() : null);
        response.setDateOfBirth(user.getDateOfBirth() != null ? user.getDateOfBirth().getFormattedValue() : null);
        response.setPhone(user.getPhone() != null ? user.getPhone().getValue() : null);
        response.setAvatar(user.getAvatar() != null ? user.getAvatar().getUrl() : null);
        response.setActive(user.isActive());
        response.setRate(user.getRate());
        response.setRole(user.getRole());
        response.setCoin(user.getCoin());
        // Disabled: income/financial setup flags removed from User (user_income / user_financial_profile tables removed).
        // response.setIncomeSetupCompleted(user.isIncomeSetupCompleted());
        // response.setFinancialSetupCompleted(user.isFinancialSetupCompleted());
        // response.setOnboardingCompleted(user.isIncomeSetupCompleted() && user.isFinancialSetupCompleted());
        return response;
    }

}
