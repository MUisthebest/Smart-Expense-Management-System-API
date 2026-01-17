package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.dtos.requests.auth.FacebookLoginRequest;
import com.example.smartmoney.application.dtos.responses.AuthResponse;
import com.example.smartmoney.application.dtos.responses.UserResponse;
import com.example.smartmoney.application.ports.AuthPort;
import com.example.smartmoney.domain.models.user.*;
import com.example.smartmoney.domain.models.user.valueobjects.*;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.infrastructure.config.FacebookOAuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class FacebookLoginUseCase {
    
    private static final Logger logger = LoggerFactory.getLogger(FacebookLoginUseCase.class);
    
    private final UserRepository userRepository;
    private final AuthPort authPort;
    private final RestTemplate restTemplate;
    private final FacebookOAuthProperties facebookProperties;
    
    public FacebookLoginUseCase(
            UserRepository userRepository, 
            AuthPort authPort,
            RestTemplate restTemplate,
            FacebookOAuthProperties facebookProperties) {
        
        // Validate configuration
        if (facebookProperties.getAppId() == null || facebookProperties.getAppId().isBlank()) {
            throw new IllegalStateException("Facebook App ID is not configured");
        }
        if (facebookProperties.getAppSecret() == null || facebookProperties.getAppSecret().isBlank()) {
            throw new IllegalStateException("Facebook App Secret is not configured");
        }
        
        this.userRepository = userRepository;
        this.authPort = authPort;
        this.restTemplate = restTemplate;
        this.facebookProperties = facebookProperties;
        
        logger.info("FacebookLoginUseCase initialized with: {}", facebookProperties);
    }
    
    public AuthResponse execute(FacebookLoginRequest request) {
        try {
            // Debug logging
            logger.debug("Verifying Facebook token for app: {}...", 
                facebookProperties.getAppId().substring(0, Math.min(5, facebookProperties.getAppId().length())));
            
            // Verify Facebook Access Token
            Map<String, Object> userInfo = verifyFacebookToken(request.getAccessToken());
            
            if (userInfo == null || userInfo.isEmpty()) {
                throw new DomainException("Invalid Facebook access token");
            }
            
            // Extract user information from Facebook
            String email = (String) userInfo.get("email");
            String name = (String) userInfo.get("name");
            String picture = extractPictureUrl(userInfo);
            String facebookId = (String) userInfo.get("id");
            
            if (email == null || email.isBlank()) {
                throw new DomainException("Email not found in Facebook profile");
            }
            
            Email emailVO = new Email(email);
            
            // Find or create user
            User user = userRepository.findByEmail(emailVO)
                    .orElseGet(() -> createUserFromFacebook(email, name, picture, facebookId));
            
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
                    facebookProperties.getTokenValiditySeconds().longValue(),
                    userResponse
            );
            
        } catch (Exception e) {
            logger.error("Facebook login verification failed", e);
            throw new DomainException("Facebook authentication failed: " + e.getMessage());
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> verifyFacebookToken(String accessToken) {
        try {
            // First, debug the token to check validity
            Map<String, Object> debugResponse = debugFacebookToken(accessToken);
            
            if (!isTokenValid(debugResponse)) {
                logger.error("Facebook token is invalid: {}", debugResponse);
                return null;
            }
            
            // Then, get user info
            String url = UriComponentsBuilder
                    .fromUriString(facebookProperties.getGraphApiUrl())
                    .queryParam("access_token", accessToken)
                    .queryParam("fields", facebookProperties.getFields())
                    .build()
                    .toUriString();
            
            logger.debug("Fetching user info from Facebook Graph API: {}", url.replace(accessToken, "***"));
            
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            // Check if response contains error
            if (response != null && response.containsKey("error")) {
                logger.error("Facebook Graph API error: {}", response.get("error"));
                return null;
            }
            
            return response;
            
        } catch (Exception e) {
            logger.error("Failed to verify Facebook token", e);
            return null;
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> debugFacebookToken(String inputToken) {
        try {
            String debugUrl = facebookProperties.getDebugTokenUrl(inputToken);
            logger.debug("Debugging Facebook token: {}", debugUrl.replace(inputToken, "***"));
            
            return restTemplate.getForObject(debugUrl, Map.class);
            
        } catch (Exception e) {
            logger.error("Failed to debug Facebook token", e);
            return null;
        }
    }
    
    @SuppressWarnings("unchecked")
    private boolean isTokenValid(Map<String, Object> debugResponse) {
        if (debugResponse == null || debugResponse.containsKey("error")) {
            return false;
        }
        
        Map<String, Object> data = (Map<String, Object>) debugResponse.get("data");
        if (data == null) {
            return false;
        }
        
        // Check if token is valid
        Boolean isValid = (Boolean) data.get("is_valid");
        if (isValid == null || !isValid) {
            return false;
        }
        
        // Check if token is for our app
        String appId = (String) data.get("app_id");
        if (appId == null || !appId.equals(facebookProperties.getAppId())) {
            return false;
        }
        
        // Check if token has expired
        Map<String, Object> expiresAt = (Map<String, Object>) data.get("expires_at");
        if (expiresAt != null) {
            Long expiresAtValue = (Long) expiresAt.get("value");
            if (expiresAtValue != null && expiresAtValue < System.currentTimeMillis() / 1000) {
                return false;
            }
        }
        
        return true;
    }
    
    @SuppressWarnings("unchecked")
    private String extractPictureUrl(Map<String, Object> userInfo) {
        try {
            Object pictureObj = userInfo.get("picture");
            if (pictureObj instanceof Map) {
                Map<String, Object> pictureMap = (Map<String, Object>) pictureObj;
                Object dataObj = pictureMap.get("data");
                if (dataObj instanceof Map) {
                    Map<String, Object> dataMap = (Map<String, Object>) dataObj;
                    return (String) dataMap.get("url");
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to extract picture URL from Facebook response", e);
        }
        return null;
    }
    
    private User createUserFromFacebook(String email, String name, String picture, String facebookId) {
        try {
            // Generate username from email (email prefix)
            String username = email.split("@")[0] + "_" + UUID.randomUUID().toString().substring(0, 8);
            
            // Create new user with Facebook OAuth
            User newUser = User.createFacebookUser(
                    username,
                    email,
                    name != null ? name : email,
                    picture != null ? new Avatar(picture) : null
            );
            
            logger.info("Creating new user from Facebook OAuth: {} (Facebook ID: {})", email, facebookId);
            return userRepository.save(newUser);
            
        } catch (Exception e) {
            logger.error("Failed to create user from Facebook data", e);
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