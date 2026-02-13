package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.dtos.requests.auth.FacebookLoginRequest;
import com.example.smartmoney.application.dtos.responses.AuthResponse;
import com.example.smartmoney.application.ports.auth.AuthPort;
import com.example.smartmoney.application.ports.auth.ExternalOAuthPort;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.valueobjects.Email;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.exceptions.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FacebookLoginUseCase Tests - External JWT Validation")
class FacebookLoginUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthPort authPort;

    @Mock
    private ExternalOAuthPort externalOAuthPort;

    private FacebookLoginUseCase facebookLoginUseCase;

    private FacebookLoginRequest validRequest;
    private Map<String, Object> supabaseUserClaims;

    @BeforeEach
    void setUp() {
        // Initialize the use case manually with mocked dependencies
        facebookLoginUseCase = new FacebookLoginUseCase(userRepository, authPort, externalOAuthPort);
        
        validRequest = new FacebookLoginRequest();
        validRequest.setAccessToken("supabase-jwt-token");

        // Simulate Supabase JWT claims
        supabaseUserClaims = new HashMap<>();
        supabaseUserClaims.put("sub", "user-id-123");
        supabaseUserClaims.put("email", "test@example.com");
        supabaseUserClaims.put("name", "Test User");
    }

    @Test
    @DisplayName("Should successfully login with valid Supabase JWT and existing user")
    void testExecuteWithValidSupabaseJwtAndExistingUser() throws Exception {
        // Arrange
        User existingUser = User.builder()
                .id(new UUID(0L, 0L))
                .username("testuser")
                .email(new Email("test@example.com"))
                .fullName("Test User")
                .active(true)
                .dateOfBirth(new com.example.smartmoney.domain.models.user.valueobjects.DateOfBirth("01/01/1990"))
                .phone(new com.example.smartmoney.domain.models.user.valueobjects.Phone("0123456789"))
                .build();
        
        when(externalOAuthPort.validateAndExtractExternalToken("supabase-jwt-token"))
                .thenAnswer(invocation -> supabaseUserClaims);
        when(userRepository.findByEmail(any(Email.class)))
                .thenReturn(Optional.of(existingUser));
        when(authPort.generateAccessToken(any(User.class)))
                .thenReturn("backend-access-token");
        when(authPort.generateRefreshToken(any(User.class)))
                .thenReturn("backend-refresh-token");

        // Act
        AuthResponse response = facebookLoginUseCase.execute(validRequest);

        // Assert
        assertNotNull(response);
        assertEquals("backend-access-token", response.getAccessToken());
        assertEquals("backend-refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(0, response.getExpiresIn());
        assertNotNull(response.getUser());
        assertEquals("test@example.com", response.getUser().getEmail());

        verify(externalOAuthPort, times(1)).validateAndExtractExternalToken("supabase-jwt-token");
        verify(userRepository, times(1)).findByEmail(any(Email.class));
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should create new user if not found in database")
    void testExecuteCreatesNewUserWhenNotFound() throws Exception {
        // Arrange
        when(externalOAuthPort.validateAndExtractExternalToken("supabase-jwt-token"))
                .thenAnswer(invocation -> supabaseUserClaims);
        when(userRepository.findByEmail(any(Email.class)))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setId(new UUID(0L, 0L));
                    return user;
                });
        when(authPort.generateAccessToken(any(User.class)))
                .thenReturn("backend-access-token");
        when(authPort.generateRefreshToken(any(User.class)))
                .thenReturn("backend-refresh-token");

        // Act
        AuthResponse response = facebookLoginUseCase.execute(validRequest);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getUser());
        assertEquals("test@example.com", response.getUser().getEmail());
        assertEquals("Test User", response.getUser().getFullName());

        verify(userRepository, times(2)).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw exception when external JWT is invalid")
    void testExecuteThrowsExceptionWhenJwtIsInvalid() throws Exception {
        // Arrange
        when(externalOAuthPort.validateAndExtractExternalToken("invalid-jwt-token"))
                .thenThrow(new RuntimeException("Invalid JWT token"));

        // Act & Assert
        assertThrows(DomainException.class, () -> facebookLoginUseCase.execute(validRequest));
    }

    @Test
    @DisplayName("Should throw exception when email is missing from JWT claims")
    void testExecuteThrowsExceptionWhenEmailMissing() throws Exception {
        // Arrange
        Map<String, Object> claimsWithoutEmail = new HashMap<>(supabaseUserClaims);
        claimsWithoutEmail.remove("email");

        when(externalOAuthPort.validateAndExtractExternalToken("supabase-jwt-token"))
                .thenAnswer(invocation -> claimsWithoutEmail);

        // Act & Assert
        assertThrows(DomainException.class, () -> facebookLoginUseCase.execute(validRequest));
    }

    @Test
    @DisplayName("Should reactivate inactive user on successful login")
    void testExecuteReactivatesInactiveUser() throws Exception {
        // Arrange
        User inactiveUser = User.builder()
                .id(new UUID(0L, 0L))
                .username("testuser")
                .email(new Email("test@example.com"))
                .fullName("Test User")
                .active(false)
                .dateOfBirth(new com.example.smartmoney.domain.models.user.valueobjects.DateOfBirth("01/01/1990"))
                .phone(new com.example.smartmoney.domain.models.user.valueobjects.Phone("0123456789"))
                .build();

        when(externalOAuthPort.validateAndExtractExternalToken("supabase-jwt-token"))
                .thenAnswer(invocation -> supabaseUserClaims);
        when(userRepository.findByEmail(any(Email.class)))
                .thenReturn(Optional.of(inactiveUser));
        when(authPort.generateAccessToken(any(User.class)))
                .thenReturn("backend-access-token");
        when(authPort.generateRefreshToken(any(User.class)))
                .thenReturn("backend-refresh-token");

        // Act
        AuthResponse response = facebookLoginUseCase.execute(validRequest);

        // Assert
        assertNotNull(response);
        assertTrue(inactiveUser.isActive());
        verify(userRepository, times(2)).save(any(User.class));
    }

    @Test
    @DisplayName("Should return user response with correct data from JWT")
    void testExecuteReturnsCorrectUserResponse() throws Exception {
        // Arrange
        User user = User.builder()
                .id(new UUID(0L, 0L))
                .username("testuser")
                .email(new Email("test@example.com"))
                .fullName("Test User")
                .coin(0)
                .active(true)
                .dateOfBirth(new com.example.smartmoney.domain.models.user.valueobjects.DateOfBirth("01/01/1990"))
                .phone(new com.example.smartmoney.domain.models.user.valueobjects.Phone("0123456789"))
                .build();

        when(externalOAuthPort.validateAndExtractExternalToken("supabase-jwt-token"))
                .thenAnswer(invocation -> supabaseUserClaims);
        when(userRepository.findByEmail(any(Email.class)))
                .thenReturn(Optional.of(user));
        when(authPort.generateAccessToken(any(User.class)))
                .thenReturn("backend-access-token");
        when(authPort.generateRefreshToken(any(User.class)))
                .thenReturn("backend-refresh-token");

        // Act
        AuthResponse response = facebookLoginUseCase.execute(validRequest);

        // Assert
        assertNotNull(response.getUser());
        assertEquals(new UUID(0L, 0L), response.getUser().getId());
        assertEquals("testuser", response.getUser().getUsername());
        assertEquals("test@example.com", response.getUser().getEmail());
        assertEquals("Test User", response.getUser().getFullName());
        assertEquals(0, response.getUser().getCoin());
        assertTrue(response.getUser().isActive());
    }
}
