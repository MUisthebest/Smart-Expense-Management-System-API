package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.requests.auth.*;
import com.example.smartmoney.application.dtos.responses.*;
import com.example.smartmoney.application.usecases.auth.*;
import com.example.smartmoney.application.usecases.user.DeleteFinancialSetupUseCase;
import com.example.smartmoney.domain.exceptions.AuthenticationException;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.exceptions.InvalidRefreshTokenException;
import com.example.smartmoney.infrastructure.security.JwtTokenProvider;
import com.example.smartmoney.application.usecases.user.GetFinancialSetupUseCase;
import com.example.smartmoney.application.usecases.user.UpdateFinancialSetupUseCase;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController")
class AuthControllerTest {

    @Mock private RegisterUseCase registerUseCase;
    @Mock private LoginUseCase loginUseCase;
    @Mock private RefreshTokenUseCase refreshTokenUseCase;
    @Mock private LogoutUseCase logoutUseCase;
    @Mock private GoogleLoginUseCase googleLoginUseCase;
    @Mock private FacebookLoginUseCase facebookLoginUseCase;
    @Mock private VerifyEmailUseCase verifyEmailUseCase;
    @Mock private UpdateUserUseCase updateUserUseCase;
    @Mock private SendResetPasswordOtpUseCase sendResetPasswordOtpUseCase;
    @Mock private ResetPasswordUseCase resetPasswordUseCase;
    @Mock private VerifyOtpUseCase verifyOtpUseCase;
    @Mock private CheckOtpExistsUseCase checkOtpExistsUseCase;
    @Mock private UserNotificationUseCase notificationUseCase;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private GetCurrentUserUseCase getCurrentUserUseCase;
    @Mock private GetFinancialSetupUseCase getFinancialSetupUseCase;
    @Mock private UpdateFinancialSetupUseCase updateFinancialSetupUseCase;
    @Mock private DeleteFinancialSetupUseCase deleteFinancialSetupUseCase;
    @InjectMocks
    private AuthController controller;
    private Validator validator;

    private static final UUID TEST_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String VALID_AUTH_HEADER = "Bearer valid-test-token";
    private static final String VALID_TOKEN = "valid-test-token";

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ========================== REGISTER ==========================

    @Nested
    @DisplayName("POST /register")
    class Register {

        @Test
        @DisplayName("returns 200 with success=true when registration succeeds")
        void registrationSucceeds() {
            RegisterRequest req = validRegisterRequest();
            when(registerUseCase.execute(any(RegisterRequest.class))).thenReturn(null);

            ResponseEntity<CheckResponse<Void>> response = controller.register(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).contains("Registration successful");
            verify(registerUseCase).execute(req);
        }

        @Test
        @DisplayName("returns 400 with success=false when use case throws exception")
        void registrationFailsOnDuplicateEmail() {
            RegisterRequest req = validRegisterRequest();
            doThrow(new IllegalArgumentException("Email already exists"))
                    .when(registerUseCase).execute(any());

            ResponseEntity<CheckResponse<Void>> response = controller.register(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getMessage()).contains("Registration failed");
        }

        @Test
        @DisplayName("RegisterRequest: username blank fails validation")
        void usernameBlankFailsValidation() {
            RegisterRequest req = validRegisterRequest();
            req.setUsername("");
            Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("username"));
        }

        @Test
        @DisplayName("RegisterRequest: username exceeds 20 chars fails validation")
        void usernameTooLongFailsValidation() {
            RegisterRequest req = validRegisterRequest();
            req.setUsername("a".repeat(21));
            Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("username"));
        }

        @Test
        @DisplayName("RegisterRequest: invalid email format fails validation")
        void invalidEmailFailsValidation() {
            RegisterRequest req = validRegisterRequest();
            req.setEmail("not-an-email");
            Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
        }

        @Test
        @DisplayName("RegisterRequest: password shorter than 6 chars fails validation")
        void passwordTooShortFailsValidation() {
            RegisterRequest req = validRegisterRequest();
            req.setPassword("abc");
            Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
        }

        @Test
        @DisplayName("RegisterRequest: wrong date format fails validation")
        void wrongDateFormatFailsValidation() {
            RegisterRequest req = validRegisterRequest();
            req.setDateOfBirth("1990-01-15");  // wrong format, should be dd/MM/yyyy
            Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("dateOfBirth"));
        }

        @Test
        @DisplayName("RegisterRequest: phone with letters fails validation")
        void phoneWithLettersFailsValidation() {
            RegisterRequest req = validRegisterRequest();
            req.setPhone("0901abc123");
            Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("phone"));
        }

        @Test
        @DisplayName("RegisterRequest: valid request passes all validations")
        void validRequestPassesAllValidations() {
            RegisterRequest req = validRegisterRequest();
            Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);
            assertThat(violations).isEmpty();
        }
    }

    // ========================== VERIFY EMAIL ==========================

    @Nested
    @DisplayName("POST /verify-email")
    class VerifyEmail {

        @Test
        @DisplayName("returns 200 with success=true on valid OTP")
        void verifyEmailSuccess() {
            VerifyEmailRequest req = new VerifyEmailRequest();
            req.setEmail("test@example.com");
            req.setOtp("123456");
            doNothing().when(verifyEmailUseCase).execute(any());

            ResponseEntity<CheckResponse<Void>> response = controller.verifyEmail(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("returns 200 with success=true even on invalid OTP (email enumeration prevention)")
        void verifyEmailReturnsTrueOnInvalidOtp() {
            VerifyEmailRequest req = new VerifyEmailRequest();
            req.setEmail("test@example.com");
            req.setOtp("000000");
            doThrow(new RuntimeException("Invalid OTP")).when(verifyEmailUseCase).execute(any());

            ResponseEntity<CheckResponse<Void>> response = controller.verifyEmail(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getMessage()).contains("Invalid OTP");
        }
    }

    // ========================== LOGIN ==========================

    @Nested
    @DisplayName("POST /login")
    class Login {

        @Test
        @DisplayName("returns 200 with AuthResponse on valid credentials")
        void loginSucceeds() {
            LoginRequest req = new LoginRequest();
            req.setEmail("user@example.com");
            req.setPassword("password123");
            AuthResponse authResponse = buildAuthResponse();
            when(loginUseCase.execute(any())).thenReturn(authResponse);

            ResponseEntity<CheckResponse<AuthResponse>> response = controller.login(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData()).isEqualTo(authResponse);
            assertThat(response.getBody().getMessage()).contains("Login successful");
        }

        @Test
        @DisplayName("returns 400 with success=false on invalid credentials")
        void loginFailsOnBadCredentials() {
            LoginRequest req = new LoginRequest();
            req.setEmail("user@example.com");
            req.setPassword("wrongpassword");
            when(loginUseCase.execute(any())).thenThrow(new AuthenticationException("Invalid credentials"));

            ResponseEntity<CheckResponse<AuthResponse>> response = controller.login(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getMessage()).contains("Login failed");
        }

        @Test
        @DisplayName("LoginRequest: blank email fails validation")
        void blankEmailFailsValidation() {
            LoginRequest req = new LoginRequest();
            req.setEmail("");
            req.setPassword("password123");
            Set<ConstraintViolation<LoginRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
        }

        @Test
        @DisplayName("LoginRequest: invalid email format fails validation")
        void invalidEmailFormatFailsValidation() {
            LoginRequest req = new LoginRequest();
            req.setEmail("not-email");
            req.setPassword("password123");
            Set<ConstraintViolation<LoginRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
        }

        @Test
        @DisplayName("LoginRequest: blank password fails validation")
        void blankPasswordFailsValidation() {
            LoginRequest req = new LoginRequest();
            req.setEmail("user@example.com");
            req.setPassword("");
            Set<ConstraintViolation<LoginRequest>> violations = validator.validate(req);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
        }
    }

    // ========================== GOOGLE LOGIN ==========================

    @Nested
    @DisplayName("POST /login/google")
    class GoogleLogin {

        @Test
        @DisplayName("returns 200 with AuthResponse on valid Google token")
        void googleLoginSucceeds() {
            GoogleLoginRequest req = new GoogleLoginRequest();
            req.setIdToken("google-id-token");
            AuthResponse authResponse = buildAuthResponse();
            when(googleLoginUseCase.execute(any())).thenReturn(authResponse);

            ResponseEntity<CheckResponse<AuthResponse>> response = controller.googleLogin(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData()).isEqualTo(authResponse);
        }

        @Test
        @DisplayName("returns 401 when Google token is invalid")
        void googleLoginFailsOnInvalidToken() {
            GoogleLoginRequest req = new GoogleLoginRequest();
            req.setIdToken("invalid-token");
            when(googleLoginUseCase.execute(any())).thenThrow(new AuthenticationException("Invalid Google token"));

            ResponseEntity<CheckResponse<AuthResponse>> response = controller.googleLogin(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().isSuccess()).isFalse();
        }
    }

    // ========================== FACEBOOK LOGIN ==========================

    @Nested
    @DisplayName("POST /login/facebook")
    class FacebookLogin {

        @BeforeEach
        void wireFacebookLoginUseCase() {
            // @InjectMocks does not wrap a bare @Mock into the Optional<FacebookLoginUseCase>
            // constructor parameter, so build the controller explicitly for these tests.
            controller = new AuthController(
                    registerUseCase, loginUseCase, refreshTokenUseCase, logoutUseCase,
                    googleLoginUseCase, Optional.of(facebookLoginUseCase), getCurrentUserUseCase,
                    verifyEmailUseCase, updateUserUseCase, sendResetPasswordOtpUseCase,
                    resetPasswordUseCase, verifyOtpUseCase, checkOtpExistsUseCase, notificationUseCase, jwtTokenProvider, getFinancialSetupUseCase,
                    updateFinancialSetupUseCase, deleteFinancialSetupUseCase);
        }

        @Test
        @DisplayName("returns 200 with AuthResponse on valid Facebook token")
        void facebookLoginSucceeds() {
            FacebookLoginRequest req = new FacebookLoginRequest();
            req.setAccessToken("facebook-access-token");
            AuthResponse authResponse = buildAuthResponse();
            when(facebookLoginUseCase.execute(any())).thenReturn(authResponse);

            ResponseEntity<CheckResponse<AuthResponse>> response = controller.facebookLogin(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("returns 401 when Facebook token is invalid")
        void facebookLoginFailsOnInvalidToken() {
            FacebookLoginRequest req = new FacebookLoginRequest();
            req.setAccessToken("invalid-token");
            when(facebookLoginUseCase.execute(any())).thenThrow(new AuthenticationException("Invalid Facebook token"));

            ResponseEntity<CheckResponse<AuthResponse>> response = controller.facebookLogin(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().isSuccess()).isFalse();
        }

        @Test
        @DisplayName("returns 401 when Facebook login is not enabled (Optional.empty)")
        void facebookLoginDisabledWhenOptionalEmpty() {
            AuthController controllerWithoutFacebook = new AuthController(
                    registerUseCase, loginUseCase, refreshTokenUseCase, logoutUseCase,
                    googleLoginUseCase, Optional.<FacebookLoginUseCase>empty(), getCurrentUserUseCase,
                    verifyEmailUseCase, updateUserUseCase, sendResetPasswordOtpUseCase,
                    resetPasswordUseCase, verifyOtpUseCase, checkOtpExistsUseCase, notificationUseCase, jwtTokenProvider, getFinancialSetupUseCase,
                    updateFinancialSetupUseCase, deleteFinancialSetupUseCase);

            FacebookLoginRequest req = new FacebookLoginRequest();
            req.setAccessToken("any-token");

            ResponseEntity<CheckResponse<AuthResponse>> response = controllerWithoutFacebook.facebookLogin(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getMessage()).contains("Facebook login is not enabled");
        }
    }

    // ========================== REFRESH TOKEN ==========================

    @Nested
    @DisplayName("POST /refresh-token")
    class RefreshToken {

        @Test
        @DisplayName("returns 200 with new AuthResponse on valid refresh token")
        void refreshTokenSucceeds() {
            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken("valid-refresh-token");
            AuthResponse authResponse = buildAuthResponse();
            when(refreshTokenUseCase.execute(any())).thenReturn(authResponse);

            ResponseEntity<CheckResponse<AuthResponse>> response = controller.refreshToken(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData()).isEqualTo(authResponse);
        }

        @Test
        @DisplayName("returns 401 when refresh token is expired")
        void refreshTokenFailsOnExpiredToken() {
            RefreshTokenRequest req = new RefreshTokenRequest();
            req.setRefreshToken("expired-token");
            when(refreshTokenUseCase.execute(any())).thenThrow(new InvalidRefreshTokenException("Refresh token has expired"));

            ResponseEntity<CheckResponse<AuthResponse>> response = controller.refreshToken(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().isSuccess()).isFalse();
        }
    }

    // ========================== LOGOUT ==========================

    @Nested
    @DisplayName("POST /logout")
    class Logout {

        @Test
        @DisplayName("returns 200 with success=true on valid Bearer token")
        void logoutSucceeds() {
            when(jwtTokenProvider.getUserIdFromToken(VALID_TOKEN)).thenReturn(TEST_USER_ID);
            doNothing().when(logoutUseCase).execute(TEST_USER_ID);

            ResponseEntity<CheckResponse<Void>> response = controller.logout(VALID_AUTH_HEADER);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).contains("Logout successful");
        }

        @Test
        @DisplayName("returns 401 when Authorization header is missing Bearer prefix")
        void logoutFailsOnInvalidHeader() {
            ResponseEntity<CheckResponse<Void>> response = controller.logout("InvalidHeader token");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().isSuccess()).isFalse();
        }
    }

    // ========================== HEALTH ==========================

    @Nested
    @DisplayName("GET /health")
    class Health {

        @Test
        @DisplayName("returns 200 with OK data")
        void healthReturnsOk() {
            ResponseEntity<CheckResponse<String>> response = controller.health();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData()).isEqualTo("OK");
        }
    }

    // ========================== GET CURRENT USER ==========================
    @Nested
    @DisplayName("GET /me")
    class GetCurrentUser {

        @Test
        void getCurrentUserSuccess() {
            UserResponse user = new UserResponse();
            user.setId(TEST_USER_ID);

            when(getCurrentUserUseCase.execute(TEST_USER_ID)).thenReturn(user);

            ResponseEntity<CheckResponse<UserResponse>> response =
                    controller.getCurrentUser(TEST_USER_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData()).isEqualTo(user);
        }
    }
    // ========================== UPDATE USER ==========================

    @Nested
    @DisplayName("PUT /me")
    class UpdateUser {

        @Test
        @DisplayName("returns 200 with updated UserResponse on success")
        void updateUserSucceeds() {
            UpdateUserRequest req = new UpdateUserRequest();
            req.setFullName("Updated Name");
            UserResponse userResponse = new UserResponse();
            userResponse.setId(TEST_USER_ID);
            userResponse.setFullName("Updated Name");
            when(updateUserUseCase.execute(eq(TEST_USER_ID), any())).thenReturn(userResponse);

            ResponseEntity<CheckResponse<UserResponse>> response = controller.updateUser(TEST_USER_ID, req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData().getFullName()).isEqualTo("Updated Name");
        }

        @Test
        @DisplayName("returns 409 when phone number is already in use")
        void updateUserConflictOnDuplicatePhone() {
            UpdateUserRequest req = new UpdateUserRequest();
            req.setPhone("0901234567");
            when(updateUserUseCase.execute(eq(TEST_USER_ID), any()))
                    .thenThrow(new RuntimeException("Phone number already in use"));

            ResponseEntity<CheckResponse<UserResponse>> response = controller.updateUser(TEST_USER_ID, req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().isSuccess()).isFalse();
        }

        @Test
        @DisplayName("returns 400 on generic update failure")
        void updateUserReturnsBadRequestOnGenericError() {
            UpdateUserRequest req = new UpdateUserRequest();
            when(updateUserUseCase.execute(eq(TEST_USER_ID), any()))
                    .thenThrow(new RuntimeException("Validation error"));

            ResponseEntity<CheckResponse<UserResponse>> response = controller.updateUser(TEST_USER_ID, req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().isSuccess()).isFalse();
        }
    }

    // ========================== FORGOT PASSWORD ==========================

    @Nested
    @DisplayName("POST /forgot")
    class ForgotPassword {

        @Test
        @DisplayName("returns 200 always (prevents email enumeration)")
        void forgotPasswordReturns200ForExistingEmail() {
            SendResetPasswordOtpRequest req = new SendResetPasswordOtpRequest();
            req.setEmail("user@example.com");
            doNothing().when(sendResetPasswordOtpUseCase).execute(any());

            ResponseEntity<CheckResponse<Void>> response = controller.forgotPassword(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("returns 400 on failure to process request")
        void forgotPasswordReturnsBadRequestOnError() {
            SendResetPasswordOtpRequest req = new SendResetPasswordOtpRequest();
            req.setEmail("user@example.com");
            doThrow(new RuntimeException("SMTP error")).when(sendResetPasswordOtpUseCase).execute(any());

            ResponseEntity<CheckResponse<Void>> response = controller.forgotPassword(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().isSuccess()).isFalse();
        }
    }

    // ========================== VERIFY OTP ==========================

    @Nested
    @DisplayName("POST /verify-otp")
    class VerifyOtp {

        @Test
        @DisplayName("returns 200 with reset token on valid OTP")
        void verifyOtpSucceeds() {
            VerifyOtpRequest req = new VerifyOtpRequest();
            req.setEmail("user@example.com");
            req.setOtp("123456");
            VerifyOtpResponse otpResponse = VerifyOtpResponse.builder()
                    .resetToken("reset-uuid-token").email("user@example.com").build();
            when(verifyOtpUseCase.execute(any())).thenReturn(otpResponse);

            ResponseEntity<CheckResponse<VerifyOtpResponse>> response = controller.verifyOtp(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData().getResetToken()).isEqualTo("reset-uuid-token");
        }

        @Test
        @DisplayName("returns 400 on invalid OTP (DomainException)")
        void verifyOtpFailsOnInvalidOtp() {
            VerifyOtpRequest req = new VerifyOtpRequest();
            req.setEmail("user@example.com");
            req.setOtp("000000");
            when(verifyOtpUseCase.execute(any()))
                    .thenThrow(new DomainException("Invalid OTP"));

            ResponseEntity<CheckResponse<VerifyOtpResponse>> response = controller.verifyOtp(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().isSuccess()).isFalse();
        }

        @Test
        @DisplayName("returns 404 when user not found")
        void verifyOtpReturns404WhenUserNotFound() {
            VerifyOtpRequest req = new VerifyOtpRequest();
            req.setEmail("noone@example.com");
            req.setOtp("123456");
            when(verifyOtpUseCase.execute(any()))
                    .thenThrow(new DomainException("User not found for this email"));

            ResponseEntity<CheckResponse<VerifyOtpResponse>> response = controller.verifyOtp(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("returns 500 on unexpected error")
        void verifyOtpReturns500OnUnexpectedError() {
            VerifyOtpRequest req = new VerifyOtpRequest();
            req.setEmail("user@example.com");
            req.setOtp("123456");
            when(verifyOtpUseCase.execute(any())).thenThrow(new NullPointerException("NPE"));

            ResponseEntity<CheckResponse<VerifyOtpResponse>> response = controller.verifyOtp(req);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ========================== RESET PASSWORD ==========================

    @Nested
    @DisplayName("POST /reset-password")
    class ResetPassword {

        @Test
        @DisplayName("returns 200 with success=true on valid reset token and new password")
        void resetPasswordSucceeds() {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setNewPassword("newPassword123");
            doNothing().when(resetPasswordUseCase).execute(any(), any());

            ResponseEntity<CheckResponse<Void>> response = controller.resetPassword(req, "valid-reset-token");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
        }

        @Test
        @DisplayName("returns 401 when reset token is missing")
        void resetPasswordFailsWhenTokenMissing() {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setNewPassword("newPassword123");

            ResponseEntity<CheckResponse<Void>> response = controller.resetPassword(req, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getMessage()).contains("Reset token is required");
        }

        @Test
        @DisplayName("returns 401 when reset token is expired or invalid")
        void resetPasswordFailsOnExpiredToken() {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setNewPassword("newPassword123");
            doThrow(new DomainException("Invalid or expired reset token"))
                    .when(resetPasswordUseCase).execute(any(), any());

            ResponseEntity<CheckResponse<Void>> response = controller.resetPassword(req, "expired-token");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("returns 404 when user not found during reset")
        void resetPasswordReturns404WhenUserNotFound() {
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.setNewPassword("newPassword123");
            doThrow(new DomainException("User not found"))
                    .when(resetPasswordUseCase).execute(any(), any());

            ResponseEntity<CheckResponse<Void>> response = controller.resetPassword(req, "some-token");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ========================== NOTIFICATION ==========================

    @Nested
    @DisplayName("Notification endpoints")
    class Notification {

        @Test
        @DisplayName("PATCH /notification/enable returns 200 with success=true")
        void enableNotificationSucceeds() {
            when(jwtTokenProvider.getUserIdFromToken(VALID_TOKEN)).thenReturn(TEST_USER_ID);
            doNothing().when(notificationUseCase).enable(TEST_USER_ID);

            ResponseEntity<CheckResponse<Boolean>> response = controller.enable(VALID_AUTH_HEADER);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Enabled");
            verify(notificationUseCase).enable(TEST_USER_ID);
        }

        @Test
        @DisplayName("PATCH /notification/disable returns 200 with success=true")
        void disableNotificationSucceeds() {
            when(jwtTokenProvider.getUserIdFromToken(VALID_TOKEN)).thenReturn(TEST_USER_ID);
            doNothing().when(notificationUseCase).disable(TEST_USER_ID);

            ResponseEntity<CheckResponse<Boolean>> response = controller.disable(VALID_AUTH_HEADER);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getMessage()).isEqualTo("Disabled");
        }

        @Test
        @DisplayName("GET /notification returns 200 with current notification status")
        void getNotificationStatusReturnsTrue() {
            when(jwtTokenProvider.getUserIdFromToken(VALID_TOKEN)).thenReturn(TEST_USER_ID);
            when(notificationUseCase.getStatus(TEST_USER_ID)).thenReturn(Boolean.TRUE);

            ResponseEntity<CheckResponse<Boolean>> response = controller.getStatus(VALID_AUTH_HEADER);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isSuccess()).isTrue();
            assertThat(response.getBody().getData()).isTrue();
        }

        @Test
        @DisplayName("GET /notification returns false when notifications are disabled")
        void getNotificationStatusReturnsFalse() {
            when(jwtTokenProvider.getUserIdFromToken(VALID_TOKEN)).thenReturn(TEST_USER_ID);
            when(notificationUseCase.getStatus(TEST_USER_ID)).thenReturn(Boolean.FALSE);

            ResponseEntity<CheckResponse<Boolean>> response = controller.getStatus(VALID_AUTH_HEADER);

            assertThat(response.getBody().getData()).isFalse();
        }
    }

    // ========================== HELPERS ==========================

    private RegisterRequest validRegisterRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("testuser");
        req.setFullName("Test User");
        req.setPassword("password123");
        req.setDateOfBirth("15/01/1990");
        req.setPhone("0901234567");
        req.setEmail("test@example.com");
        return req;
    }

    private AuthResponse buildAuthResponse() {
        UserResponse user = new UserResponse();
        user.setId(TEST_USER_ID);
        user.setEmail("user@example.com");
        user.setUsername("testuser");
        return new AuthResponse("access-token", "refresh-token", "Bearer", 3600L, user);
    }
}
