package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.dtos.requests.auth.FacebookLoginRequest;
import com.example.smartmoney.application.dtos.responses.AuthResponse;
import com.example.smartmoney.application.ports.AuthPort;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.valueobjects.Email;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.exceptions.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FacebookLoginUseCase Tests")
class FacebookLoginUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthPort authPort;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private FacebookLoginUseCase facebookLoginUseCase;

    private FacebookLoginRequest validRequest;
    private Map<String, Object> facebookUserData;

    @BeforeEach
    void setUp() {
        validRequest = new FacebookLoginRequest();
        validRequest.setAccessToken("valid_facebook_access_token");
        validRequest.setEmail("test@example.com");
        validRequest.setName("Test User");
        validRequest.setPicture("https://example.com/avatar.jpg");
        validRequest.setUserId("123456");

        facebookUserData = new HashMap<>();
        facebookUserData.put("id", "123456");
        facebookUserData.put("email", "test@example.com");
        facebookUserData.put("name", "Test User");
        
        Map<String, Object> pictureData = new HashMap<>();
        Map<String, Object> pictureDataObj = new HashMap<>();
        pictureDataObj.put("url", "https://example.com/avatar.jpg");
        pictureData.put("data", pictureDataObj);
        facebookUserData.put("picture", pictureData);
    }

    @Test
    @DisplayName("Should successfully login with valid Facebook token and existing user")
    void testExecuteWithValidTokenAndExistingUser() {
        // Arrange
        User existingUser = User.builder()
                .id(1L)
                .username("testuser")
                .email(new Email("test@example.com"))
                .fullName("Test User")
                .active(true)
                .build();

        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(facebookUserData);
        when(userRepository.findByEmail(any(Email.class)))
                .thenReturn(Optional.of(existingUser));
        when(authPort.generateAccessToken(any(User.class)))
                .thenReturn("access_token");
        when(authPort.generateRefreshToken(any(User.class)))
                .thenReturn("refresh_token");

        // Act
        AuthResponse response = facebookLoginUseCase.execute(validRequest);

        // Assert
        assertNotNull(response);
        assertEquals("access_token", response.getAccessToken());
        assertEquals("refresh_token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(3600L, response.getExpiresIn());
        assertNotNull(response.getUser());

        verify(restTemplate, times(1)).getForObject(anyString(), eq(Map.class));
        verify(userRepository, times(1)).findByEmail(any(Email.class));
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should create new user if not found")
    void testExecuteCreatesNewUserWhenNotFound() {
        // Arrange
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(facebookUserData);
        when(userRepository.findByEmail(any(Email.class)))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setId(1L);
                    return user;
                });
        when(authPort.generateAccessToken(any(User.class)))
                .thenReturn("access_token");
        when(authPort.generateRefreshToken(any(User.class)))
                .thenReturn("refresh_token");

        // Act
        AuthResponse response = facebookLoginUseCase.execute(validRequest);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getUser());
        assertEquals("test@example.com", response.getUser().getEmail());

        verify(userRepository, times(2)).save(any(User.class)); // Once for creation, once for refresh token
    }

    @Test
    @DisplayName("Should throw exception when accessToken is missing")
    void testExecuteThrowsExceptionWhenAccessTokenMissing() {
        // Arrange
        FacebookLoginRequest invalidRequest = new FacebookLoginRequest();
        invalidRequest.setAccessToken(null);

        // Act & Assert
        assertThrows(Exception.class, () -> facebookLoginUseCase.execute(invalidRequest));
    }

    @Test
    @DisplayName("Should throw exception when Facebook token is invalid")
    void testExecuteThrowsExceptionWhenTokenIsInvalid() {
        // Arrange
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", new HashMap<String, Object>());

        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(errorResponse);

        // Act & Assert
        assertThrows(DomainException.class, () -> facebookLoginUseCase.execute(validRequest));
    }

    @Test
    @DisplayName("Should throw exception when email is missing from Facebook response")
    void testExecuteThrowsExceptionWhenEmailMissing() {
        // Arrange
        Map<String, Object> dataWithoutEmail = new HashMap<>(facebookUserData);
        dataWithoutEmail.remove("email");

        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(dataWithoutEmail);

        // Act & Assert
        assertThrows(DomainException.class, () -> facebookLoginUseCase.execute(validRequest));
    }

    @Test
    @DisplayName("Should handle missing picture gracefully")
    void testExecuteHandlesMissingPicture() {
        // Arrange
        Map<String, Object> dataWithoutPicture = new HashMap<>(facebookUserData);
        dataWithoutPicture.remove("picture");

        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(dataWithoutPicture);
        when(userRepository.findByEmail(any(Email.class)))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setId(1L);
                    return user;
                });
        when(authPort.generateAccessToken(any(User.class)))
                .thenReturn("access_token");
        when(authPort.generateRefreshToken(any(User.class)))
                .thenReturn("refresh_token");

        // Act
        AuthResponse response = facebookLoginUseCase.execute(validRequest);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getUser());
    }

    @Test
    @DisplayName("Should reactivate inactive user")
    void testExecuteReactivatesInactiveUser() {
        // Arrange
        User inactiveUser = User.builder()
                .id(1L)
                .username("testuser")
                .email(new Email("test@example.com"))
                .fullName("Test User")
                .active(false)
                .build();

        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(facebookUserData);
        when(userRepository.findByEmail(any(Email.class)))
                .thenReturn(Optional.of(inactiveUser));
        when(authPort.generateAccessToken(any(User.class)))
                .thenReturn("access_token");
        when(authPort.generateRefreshToken(any(User.class)))
                .thenReturn("refresh_token");

        // Act
        AuthResponse response = facebookLoginUseCase.execute(validRequest);

        // Assert
        assertNotNull(response);
        assertTrue(inactiveUser.isActive());
        verify(userRepository, times(2)).save(any(User.class));
    }

    @Test
    @DisplayName("Should return user response with correct data")
    void testExecuteReturnsCorrectUserResponse() {
        // Arrange
        User user = User.builder()
                .id(1L)
                .username("testuser")
                .email(new Email("test@example.com"))
                .fullName("Test User")
                .coin(0)
                .active(true)
                .build();

        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(facebookUserData);
        when(userRepository.findByEmail(any(Email.class)))
                .thenReturn(Optional.of(user));
        when(authPort.generateAccessToken(any(User.class)))
                .thenReturn("access_token");
        when(authPort.generateRefreshToken(any(User.class)))
                .thenReturn("refresh_token");

        // Act
        AuthResponse response = facebookLoginUseCase.execute(validRequest);

        // Assert
        assertNotNull(response.getUser());
        assertEquals(1L, response.getUser().getId());
        assertEquals("testuser", response.getUser().getUsername());
        assertEquals("test@example.com", response.getUser().getEmail());
        assertEquals("Test User", response.getUser().getFullName());
        assertEquals(0, response.getUser().getCoin());
        assertTrue(response.getUser().isActive());
    }
}
