package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.dtos.requests.auth.GoogleLoginRequest;
import com.example.smartmoney.application.dtos.responses.AuthResponse;
import com.example.smartmoney.application.dtos.responses.UserResponse;
import com.example.smartmoney.application.ports.AuthPort;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.valueobjects.Email;
import com.example.smartmoney.domain.models.user.valueobjects.RefreshToken;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.infrastructure.config.GoogleOAuthProperties;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.UUID;

@Service
@Transactional
public class GoogleLoginUseCase {
    
    private static final Logger logger = LoggerFactory.getLogger(GoogleLoginUseCase.class);
    
    private final UserRepository userRepository;
    private final AuthPort authPort;
    private final GoogleOAuthProperties googleOAuthProperties;
    private final GoogleIdTokenVerifier verifier;
    
    public GoogleLoginUseCase(
            UserRepository userRepository, 
            AuthPort authPort,
            GoogleOAuthProperties googleOAuthProperties) {
        this.userRepository = userRepository;
        this.authPort = authPort;
        this.googleOAuthProperties = googleOAuthProperties;
        
        // Initialize GoogleIdTokenVerifier with configuration properties
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), 
                new GsonFactory())
                .setAudience(Collections.singletonList(googleOAuthProperties.getClientId()))
                .build();
        
        logger.info("GoogleLoginUseCase initialized with: {}", googleOAuthProperties);
    }
    
    public AuthResponse execute(GoogleLoginRequest request) {
        try {
            // Debug: Log configuration
            logger.debug("Using Google Client ID: {}", googleOAuthProperties.getClientId());
            logger.debug("Token validity seconds: {}", googleOAuthProperties.getTokenValiditySeconds());
            
            // Verify Google ID Token
            var idToken = verifier.verify(request.getIdToken());
            if (idToken == null) {
                throw new DomainException("Invalid Google ID token");
            }
            
            // Extract user information from token
            String email = (String) idToken.getPayload().get("email");
            String name = (String) idToken.getPayload().get("name");
            String picture = (String) idToken.getPayload().get("picture");
            
            if (email == null || email.isBlank()) {
                throw new DomainException("Email not found in Google token");
            }
            
            Email emailVO = new Email(email);
            
            // Find or create user
            User user = userRepository.findByEmail(emailVO)
                    .orElseGet(() -> createUserFromGoogle(email, name, picture));
            
            // Ensure user is active
            if (!user.isActive()) {
                // Optionally reactivate user
                user.activate();
                userRepository.save(user);
            }
            
            // Generate tokens using configured validity
            String accessToken = authPort.generateAccessToken(user);
            String refreshToken = authPort.generateRefreshToken(user);
            
            // Update refresh token in database
            user.updateRefreshToken(new RefreshToken(refreshToken, java.time.LocalDateTime.now().plusDays(7)));
            userRepository.save(user);
            
            // Build response with configured token validity
            UserResponse userResponse = mapUserToResponse(user);
            
            return new AuthResponse(
                    accessToken,
                    refreshToken,
                    "Bearer",
                    googleOAuthProperties.getTokenValiditySeconds().longValue(),
                    userResponse
            );
            
        } catch (Exception e) {
            logger.error("Google login verification failed", e);
            throw new DomainException("Google authentication failed: " + e.getMessage());
        }
    }
    
    private User createUserFromGoogle(String email, String name, String picture) {
        try {
            // Generate username from email (email prefix)
            String username = email.split("@")[0] + "_" + UUID.randomUUID().toString().substring(0, 8);
            
            // Create new user with Google OAuth
            User newUser = User.createGoogleUser(
                    username,
                    email,
                    name != null ? name : email,
                    picture != null ? new com.example.smartmoney.domain.models.user.valueobjects.Avatar(picture) : null
            );
            
            logger.info("Creating new user from Google OAuth: {}", email);
            return userRepository.save(newUser);
            
        } catch (Exception e) {
            logger.error("Failed to create user from Google data", e);
            throw new DomainException("Failed to create user account");
        }
    }
    
    private UserResponse mapUserToResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setFullName(user.getFullName());
        response.setEmail(user.getEmail().getValue());
        response.setDateOfBirth(user.getDateOfBirth().getFormattedValue());
        response.setPhone(user.getPhone().getValue());
        response.setAvatar(user.getAvatar() != null ? user.getAvatar().getUrl() : null);
        response.setActive(user.isActive());
        response.setRole(user.getRole());
        response.setCoin(user.getCoin());
        return response;
    }
}