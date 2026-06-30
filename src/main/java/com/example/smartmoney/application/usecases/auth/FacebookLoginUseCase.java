package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.dtos.requests.auth.FacebookLoginRequest;
import com.example.smartmoney.application.dtos.responses.AuthResponse;
import com.example.smartmoney.application.dtos.responses.UserResponse;
import com.example.smartmoney.application.ports.auth.AuthPort;
import com.example.smartmoney.application.ports.auth.ExternalOAuthPort;
import com.example.smartmoney.domain.models.user.*;
import com.example.smartmoney.domain.models.user.valueobjects.*;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.exceptions.DomainException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class FacebookLoginUseCase {
    
    private final UserRepository userRepository;
    private final AuthPort authPort;
    private final ExternalOAuthPort externalOAuthPort;
    
    public FacebookLoginUseCase(
            UserRepository userRepository, 
            AuthPort authPort,
            ExternalOAuthPort externalOAuthPort
        ) {
        
        this.userRepository = userRepository;
        this.authPort = authPort;
        this.externalOAuthPort = externalOAuthPort;
        
        log.info("✅ FacebookLoginUseCase initialized - Ready to validate external JWT tokens");
    }
    
    /**
     * Execute Facebook login using Supabase JWT token from frontend
     * 
     * Flow:
     * 1. Frontend authenticates with Facebook via Supabase
     * 2. Frontend sends Supabase JWT access token to backend
     * 3. Backend validates the JWT token
     * 4. Backend extracts user info from the JWT
     * 5. Backend creates or updates user account
     * 6. Backend generates its own JWT tokens
     */
    public AuthResponse execute(FacebookLoginRequest request) {
        try {
            log.info("🔐 Starting Facebook login with external JWT token");
            
            // Step 1: Validate external JWT token
            Map<String, Object> userInfo = externalOAuthPort.validateAndExtractExternalToken(request.getAccessToken());
            
            if (userInfo == null || userInfo.isEmpty()) {
                throw new DomainException("Failed to extract user info from JWT token");
            }
            
            log.debug("📦 User info extracted from JWT: {}", userInfo.keySet());
            
            // Step 2: Extract user information
            String email = extractEmail(userInfo);
            String name = extractName(userInfo);
            String supabaseUserId = extractUserId(userInfo);
            
            if (email == null || email.isBlank()) {
                throw new DomainException("Email not found in JWT token");
            }
            
            log.info("👤 User email: {}, Supabase ID: {}", email, supabaseUserId);
            
            Email emailVO = new Email(email);
            
            // Step 3: Find or create user
            User user = userRepository.findByEmail(emailVO)
                    .orElseGet(() -> createUserFromSupabase(email, name, supabaseUserId));

            if (!user.isEmailVerified()) {
                user.setEmailVerified(true);
            }
            
            // Step 4: Ensure user is active
            if (!user.isActive()) {
                user.activate();
                userRepository.save(user);
                log.info("♻️ Reactivated user: {}", email);
            }
            
            // Step 5: Generate backend tokens
            String accessToken = authPort.generateAccessToken(user);
            String refreshToken = authPort.generateRefreshToken(user);
            
            // Step 6: Update refresh token in database
            user.updateRefreshToken(new RefreshToken(refreshToken, java.time.LocalDateTime.now().plusDays(7)));
            userRepository.save(user);
            
            // Step 7: Build response
            UserResponse userResponse = mapUserToResponse(user);
            
            log.info("✅ Facebook login successful for user: {}", email);
            
            return new AuthResponse(
                    accessToken,
                    refreshToken,
                    "Bearer",
                    externalOAuthPort.getFacebookTokenValiditySeconds(),
                    userResponse
            );
            
        } catch (Exception e) {
            log.error("❌ Facebook login failed: {}", e.getMessage());
            throw new DomainException("Facebook authentication failed: " + e.getMessage());
        }
    }
    
    /**
     * Extract email from Supabase JWT claims
     */
    private String extractEmail(Map<String, Object> userInfo) {
        // Try different locations where email might be
        if (userInfo.containsKey("email")) {
            return (String) userInfo.get("email");
        }
        
        // Check in nested user object
        if (userInfo.containsKey("user")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> userObj = (Map<String, Object>) userInfo.get("user");
            if (userObj != null && userObj.containsKey("email")) {
                return (String) userObj.get("email");
            }
        }
        
        return null;
    }
    
    /**
     * Extract user name from Supabase JWT claims
     */
    private String extractName(Map<String, Object> userInfo) {
        // Try different possible name fields
        String[] possibleNameFields = {"name", "full_name", "user_metadata", "custom_claims"};
        
        for (String field : possibleNameFields) {
            if (userInfo.containsKey(field)) {
                Object value = userInfo.get(field);
                if (value instanceof String) {
                    return (String) value;
                }
                // Check if it's in metadata
                if (value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> metadata = (Map<String, Object>) value;
                    if (metadata.containsKey("full_name")) {
                        return (String) metadata.get("full_name");
                    }
                    if (metadata.containsKey("name")) {
                        return (String) metadata.get("name");
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Extract Supabase user ID from JWT claims
     */
    private String extractUserId(Map<String, Object> userInfo) {
        // Try different locations for user ID
        if (userInfo.containsKey("sub")) {
            return (String) userInfo.get("sub");
        }
        if (userInfo.containsKey("id")) {
            return (String) userInfo.get("id");
        }
        
        // Check in nested user object
        if (userInfo.containsKey("user")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> userObj = (Map<String, Object>) userInfo.get("user");
            if (userObj != null) {
                if (userObj.containsKey("id")) {
                    return (String) userObj.get("id");
                }
            }
        }
        
        return UUID.randomUUID().toString();
    }
    
    /**
     * Create new user from Supabase Facebook authentication
     */
    private User createUserFromSupabase(String email, String name, String supabaseUserId) {
        try {
            // Generate username from email prefix + random suffix
            String username = email.split("@")[0] + "_" + UUID.randomUUID().toString().substring(0, 8);
            
            // Create new user
            User newUser = User.createFacebookUser(
                    username,
                    email,
                    name != null ? name : email,
                    null  // No picture from JWT, can be added later
            );
            
            log.info("👶 Creating new user from Supabase: {} (Supabase ID: {})", email, supabaseUserId);
            return userRepository.save(newUser);
            
        } catch (Exception e) {
            log.error("❌ Failed to create user: {}", e.getMessage());
            throw new DomainException("Failed to create user account: " + e.getMessage());
        }
    }

    /**
     * Map User domain model to UserResponse DTO
     */
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
