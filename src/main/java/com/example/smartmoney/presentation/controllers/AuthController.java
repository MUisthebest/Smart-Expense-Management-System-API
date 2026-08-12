package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.requests.auth.LoginRequest;
import com.example.smartmoney.application.dtos.requests.auth.RefreshTokenRequest;
import com.example.smartmoney.application.dtos.requests.auth.RegisterRequest;
import com.example.smartmoney.application.dtos.requests.auth.ResetPasswordRequest;
import com.example.smartmoney.application.dtos.requests.auth.SendResetPasswordOtpRequest;
import com.example.smartmoney.application.dtos.requests.auth.UpdateUserRequest;
import com.example.smartmoney.application.dtos.requests.auth.VerifyEmailRequest;
import com.example.smartmoney.application.dtos.requests.auth.VerifyOtpRequest;
import com.example.smartmoney.application.dtos.requests.auth.GoogleLoginRequest;
import com.example.smartmoney.application.dtos.requests.auth.FacebookLoginRequest;
import com.example.smartmoney.application.dtos.requests.users.UpdateFinancialSetupRequest;
import com.example.smartmoney.application.dtos.responses.*;
import com.example.smartmoney.application.usecases.auth.LoginUseCase;
import com.example.smartmoney.application.usecases.auth.LogoutUseCase;
import com.example.smartmoney.application.usecases.auth.RefreshTokenUseCase;
import com.example.smartmoney.application.usecases.auth.RegisterUseCase;
import com.example.smartmoney.application.usecases.auth.GoogleLoginUseCase;
import com.example.smartmoney.application.usecases.auth.FacebookLoginUseCase;
import com.example.smartmoney.application.usecases.auth.GetCurrentUserUseCase;
import com.example.smartmoney.application.usecases.auth.VerifyEmailUseCase;
import com.example.smartmoney.application.usecases.auth.VerifyOtpUseCase;
import com.example.smartmoney.application.usecases.auth.CheckOtpExistsUseCase;
import com.example.smartmoney.application.usecases.user.DeleteFinancialSetupUseCase;
import com.example.smartmoney.application.usecases.user.GetFinancialSetupUseCase;
import com.example.smartmoney.application.usecases.user.UpdateFinancialSetupUseCase;
import com.example.smartmoney.domain.exceptions.AuthenticationException;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.exceptions.EmailNotVerifiedException;
import com.example.smartmoney.domain.exceptions.InvalidRefreshTokenException;
import com.example.smartmoney.application.usecases.auth.SendResetPasswordOtpUseCase;
import com.example.smartmoney.application.usecases.auth.UserNotificationUseCase;
import com.example.smartmoney.application.usecases.auth.ResetPasswordUseCase;
import com.example.smartmoney.application.usecases.auth.UpdateUserUseCase;
import com.example.smartmoney.infrastructure.security.CurrentUser;
import com.example.smartmoney.infrastructure.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.bind.WebDataBinder;
import java.beans.PropertyEditorSupport;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
@Tag(name = "Authentication", description = "Authentication APIs for user registration, login, and token management")
public class AuthController {

    private final RegisterUseCase registerUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final GoogleLoginUseCase googleLoginUseCase;
    private final Optional<FacebookLoginUseCase> facebookLoginUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;
    private final VerifyEmailUseCase verifyEmailUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final SendResetPasswordOtpUseCase sendResetPasswordOtpUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;
    private final VerifyOtpUseCase verifyOtpUseCase;
    private final CheckOtpExistsUseCase checkOtpExistsUseCase;
    private final UserNotificationUseCase notificationUseCase;
    private final JwtTokenProvider jwtTokenProvider;
    private final GetFinancialSetupUseCase getFinancialSetupUseCase;
    private final UpdateFinancialSetupUseCase updateFinancialSetupUseCase;
    private final DeleteFinancialSetupUseCase deleteFinancialSetupUseCase;

    public AuthController(
            RegisterUseCase registerUseCase,
            LoginUseCase loginUseCase,
            RefreshTokenUseCase refreshTokenUseCase,
            LogoutUseCase logoutUseCase,
            GoogleLoginUseCase googleLoginUseCase,
            Optional<FacebookLoginUseCase> facebookLoginUseCase,
            GetCurrentUserUseCase getCurrentUserUseCase,
            VerifyEmailUseCase verifyEmailUseCase,
            UpdateUserUseCase updateUserUseCase,
            SendResetPasswordOtpUseCase sendResetPasswordOtpUseCase,
            ResetPasswordUseCase resetPasswordUseCase,
            VerifyOtpUseCase verifyOtpUseCase,
            CheckOtpExistsUseCase checkOtpExistsUseCase,
            UserNotificationUseCase notificationUseCase,
            JwtTokenProvider jwtTokenProvider,
            GetFinancialSetupUseCase getFinancialSetupUseCase,
            UpdateFinancialSetupUseCase updateFinancialSetupUseCase,
            DeleteFinancialSetupUseCase deleteFinancialSetupUseCase) {
        this.registerUseCase = registerUseCase;
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUseCase = logoutUseCase;
        this.googleLoginUseCase = googleLoginUseCase;
        this.facebookLoginUseCase = facebookLoginUseCase;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
        this.verifyEmailUseCase = verifyEmailUseCase;
        this.updateUserUseCase = updateUserUseCase;
        this.sendResetPasswordOtpUseCase = sendResetPasswordOtpUseCase;
        this.resetPasswordUseCase = resetPasswordUseCase;
        this.verifyOtpUseCase = verifyOtpUseCase;
        this.checkOtpExistsUseCase = checkOtpExistsUseCase;
        this.notificationUseCase = notificationUseCase;
        this.jwtTokenProvider = jwtTokenProvider;
        this.getFinancialSetupUseCase = getFinancialSetupUseCase;
        this.updateFinancialSetupUseCase = updateFinancialSetupUseCase;
        this.deleteFinancialSetupUseCase = deleteFinancialSetupUseCase;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(MultipartFile.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (text == null || text.trim().isEmpty()) {
                    setValue(null);
                } else {
                    throw new IllegalArgumentException("Cannot convert String to MultipartFile");
                }
            }
        });
    }

    @Operation(summary = "Register new user", description = "Register a new user with required information")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User registered successfully", content = @Content(schema = @Schema(implementation = CheckResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input or user already exists", content = @Content(schema = @Schema(implementation = CheckResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping(value = "/register", consumes = "multipart/form-data")
    public ResponseEntity<CheckResponse<Void>> register(
            @Parameter(description = "User registration data", required = true) @Valid @ModelAttribute RegisterRequest request) {

        log.info("Register request for email: {}", request.getEmail());
        try {
            registerUseCase.execute(request);

            CheckResponse<Void> response = CheckResponse.<Void>builder()
                    .success(true)
                    .message("Registration successful. Please verify your email.")
                    .data(null)
                    .build();

            log.info("Registration successful for email: {}", request.getEmail());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.warn("Registration failed for email: {}", request.getEmail(), e);

            CheckResponse<Void> response = CheckResponse.<Void>builder()
                    .success(false)
                    .message("Registration failed: " + e.getMessage())
                    .build();

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @Operation(summary = "Verify Email to Save User", description = "Using OTP code to verify")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Save successful", content = @Content(schema = @Schema(implementation = CheckResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content(schema = @Schema(implementation = CheckResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    @PostMapping("/verify-email")
    public ResponseEntity<CheckResponse<Void>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {

        try {
            verifyEmailUseCase.execute(request);

            return ResponseEntity.ok(
                    CheckResponse.<Void>builder()
                            .success(true)
                            .message("Email verified and user created")
                            .data(null)
                            .build());
        } catch (DomainException e) {
            log.error("Failed to verify email for: {}", request.getEmail(), e);

            return ResponseEntity.badRequest().body(
                    CheckResponse.<Void>builder()
                            .success(false)
                            .message(e.getMessage())
                            .data(null)
                            .build());
        }

    }

    @Operation(summary = "Check whether OTP still exists in Redis", description = "Check if an OTP for the provided email is still stored in Redis")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Check completed", content = @Content(schema = @Schema(implementation = CheckResponse.class)))
    })
    @GetMapping("/otp-exists")
    public ResponseEntity<CheckResponse<Boolean>> checkOtpExists(@RequestParam String email) {
        boolean exists = checkOtpExistsUseCase.execute(email);
        return ResponseEntity.ok(
                CheckResponse.<Boolean>builder()
                        .success(true)
                        .message(exists ? "OTP exists in Redis" : "OTP not found in Redis")
                        .data(exists)
                        .build());
    }

    @Operation(summary = "User login", description = "Authenticate user with email and password")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    @PostMapping("/login")
    public ResponseEntity<CheckResponse<AuthResponse>> login(
            @Parameter(description = "User login credentials", required = true) @Valid @RequestBody LoginRequest request) {

        log.info("Login attempt for email: {}", request.getEmail());

        try {

            AuthResponse authResponse = loginUseCase.execute(request);

            return ResponseEntity.ok(
                    CheckResponse.<AuthResponse>builder()
                            .success(true)
                            .message("Login successful")
                            .data(authResponse)
                            .build());

        } catch (EmailNotVerifiedException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(
                            CheckResponse.<AuthResponse>builder()
                                    .success(false)
                                    .message("EMAIL_NOT_VERIFIED")
                                    .build());

        } catch (DomainException e) {

            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(
                            CheckResponse.<AuthResponse>builder()
                                    .success(false)
                                    .message("Login failed: " + e.getMessage())
                                    .build());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                            CheckResponse.<AuthResponse>builder()
                                    .success(false)
                                    .message("INTERNAL_ERROR")
                                    .build());
        }
    }

    @Operation(summary = "Google login", description = "Authenticate user with Google ID token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Google login successful", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or expired Google token"),
            @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    @PostMapping("/login/google")
    public ResponseEntity<CheckResponse<AuthResponse>> googleLogin(
            @Parameter(description = "Google login request with idToken", required = true) @Valid @RequestBody GoogleLoginRequest request) {

        log.info("Google login attempt");

        try {
            AuthResponse authResponse = googleLoginUseCase.execute(request);

            CheckResponse<AuthResponse> response = CheckResponse.<AuthResponse>builder()
                    .success(true)
                    .message("Google login successful")
                    .data(authResponse)
                    .build();

            log.info("Google login successful for user");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.warn("Google login failed", e);

            CheckResponse<AuthResponse> response = CheckResponse.<AuthResponse>builder()
                    .success(false)
                    .message("Google login failed: " + e.getMessage())
                    .build();

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    @Operation(summary = "Facebook login", description = "Authenticate user with Facebook access token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Facebook login successful", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or expired Facebook token"),
            @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    @PostMapping("/login/facebook")
    public ResponseEntity<CheckResponse<AuthResponse>> facebookLogin(
            @Parameter(description = "Facebook login request with accessToken", required = true) @Valid @RequestBody FacebookLoginRequest request) {

        log.info("Facebook login attempt");

        try {
            AuthResponse authResponse = facebookLoginUseCase
                    .orElseThrow(() -> new IllegalStateException("Facebook login is not enabled"))
                    .execute(request);

            CheckResponse<AuthResponse> response = CheckResponse.<AuthResponse>builder()
                    .success(true)
                    .message("Facebook login successful")
                    .data(authResponse)
                    .build();

            log.info("Facebook login successful for user");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.warn("Facebook login failed", e);

            CheckResponse<AuthResponse> response = CheckResponse.<AuthResponse>builder()
                    .success(false)
                    .message("Facebook login failed: " + e.getMessage())
                    .build();

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    @Operation(summary = "Refresh access token", description = "Get new access token using refresh token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    @PostMapping("/refresh-token")
    public ResponseEntity<CheckResponse<AuthResponse>> refreshToken(
            @Parameter(description = "Refresh token request", required = true) @Valid @RequestBody RefreshTokenRequest request) {

        log.debug("Refresh token request");

        try {

            AuthResponse authResponse = refreshTokenUseCase.execute(request);

            return ResponseEntity.ok(
                    CheckResponse.<AuthResponse>builder()
                            .success(true)
                            .message("Token refreshed successfully")
                            .data(authResponse)
                            .build());

        } catch (InvalidRefreshTokenException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(
                            CheckResponse.<AuthResponse>builder()
                                    .success(false)
                                    .message("INVALID_REFRESH_TOKEN")
                                    .build());
        } catch (AuthenticationException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(
                            CheckResponse.<AuthResponse>builder()
                                    .success(false)
                                    .message(e.getMessage())
                                    .build());
        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                            CheckResponse.<AuthResponse>builder()
                                    .success(false)
                                    .message("INTERNAL_ERROR")
                                    .build());
        }
    }

    @Operation(summary = "User logout", description = "Logout user and invalidate refresh token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logout successful"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/logout")
    public ResponseEntity<CheckResponse<Void>> logout(
            @Parameter(description = "Bearer token", required = true) @RequestHeader("Authorization") String authHeader) {

        try {
            String token = extractTokenFromHeader(authHeader);
            UUID userId = jwtTokenProvider.getUserIdFromToken(token);
            logoutUseCase.execute(userId);

            log.info("User {} logged out successfully", userId);
            return ResponseEntity.ok(CheckResponse.<Void>builder()
                    .success(true)
                    .message("Logout successful")
                    .build());

        } catch (Exception e) {
            log.warn("Logout failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    CheckResponse.<Void>builder()
                            .success(false)
                            .message("Logout failed: " + e.getMessage())
                            .build());
        }
    }

    @Operation(summary = "Health check", description = "Check if authentication service is running")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service is healthy")
    })
    @GetMapping("/health")
    public ResponseEntity<CheckResponse<String>> health() {
        CheckResponse<String> response = CheckResponse.<String>builder()
                .success(true)
                .message("Authentication service is running")
                .data("OK")
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get current user info", description = "Get information of currently authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User information retrieved"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/me")
    public ResponseEntity<CheckResponse<UserResponse>> getCurrentUser(
            @Parameter(hidden = true) @CurrentUser UUID userId) {

        UserResponse user = getCurrentUserUseCase.execute(userId);

        CheckResponse<UserResponse> response = CheckResponse.<UserResponse>builder()
                .success(true)
                .message("User information retrieved")
                .data(user)
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update user profile", description = "Update information of the currently authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User profile updated successfully", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data", content = @Content(schema = @Schema(implementation = CheckResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "409", description = "Phone number already in use")
    })

    @PutMapping(value = "/me", consumes = "multipart/form-data")
    public ResponseEntity<CheckResponse<UserResponse>> updateUser(
            @Parameter(hidden = true) @CurrentUser UUID userId,
            @Parameter(description = "User update data", required = true) @Valid @ModelAttribute UpdateUserRequest request) {

        log.info("Update user request for userId: {}", userId);

        try {
            UserResponse updatedUser = updateUserUseCase.execute(userId, request);

            CheckResponse<UserResponse> response = CheckResponse.<UserResponse>builder()
                    .success(true)
                    .message("User profile updated successfully")
                    .data(updatedUser)
                    .build();

            log.info("User profile updated successfully: {}", userId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to update user profile for userId: {}", userId, e);

            HttpStatus status = HttpStatus.BAD_REQUEST;
            if (e.getMessage() != null && e.getMessage().contains("already in use")) {
                status = HttpStatus.CONFLICT;
            }

            CheckResponse<UserResponse> response = CheckResponse.<UserResponse>builder()
                    .success(false)
                    .message("Failed to update profile: " + e.getMessage())
                    .build();

            return ResponseEntity.status(status).body(response);
        }
    }

    @Operation(summary = "Request password reset OTP", description = "Send OTP to user's email for password reset")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OTP sent successfully if email exists"),
            @ApiResponse(responseCode = "400", description = "Invalid email format", content = @Content(schema = @Schema(implementation = CheckResponse.class)))
    })

    @PostMapping("/forgot")
    public ResponseEntity<CheckResponse<Void>> forgotPassword(
            @Parameter(description = "Email for password reset", required = true) @Valid @RequestBody SendResetPasswordOtpRequest request) {

        log.info("Forgot password request for email: {}", request.getEmail());

        try {
            // Always return success to prevent email enumeration
            sendResetPasswordOtpUseCase.execute(request);

            CheckResponse<Void> response = CheckResponse.<Void>builder()
                    .success(true)
                    .message("If the email exists, a reset code has been sent")
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to process forgot password request for email: {}", request.getEmail(), e);

            CheckResponse<Void> response = CheckResponse.<Void>builder()
                    .success(false)
                    .message("Failed to process request: " + e.getMessage())
                    .build();

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @Operation(summary = "Verify OTP and reset password", description = "Verify OTP and set new password")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset successful"),
            @ApiResponse(responseCode = "400", description = "Invalid OTP or password requirements not met", content = @Content(schema = @Schema(implementation = CheckResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found")
    })

    // API 1: Xác thực OTP
    @PostMapping("/verify-otp")
    public ResponseEntity<CheckResponse<VerifyOtpResponse>> verifyOtp(
            @Parameter(description = "Verify OTP data", required = true) @Valid @RequestBody VerifyOtpRequest request) {

        log.info("Verify OTP request for email: {}", request.getEmail());

        try {
            VerifyOtpResponse responseData = verifyOtpUseCase.execute(request);

            CheckResponse<VerifyOtpResponse> response = CheckResponse.<VerifyOtpResponse>builder()
                    .success(true)
                    .message("OTP verified successfully")
                    .data(responseData)
                    .build();

            log.info("OTP verified successfully for email: {}", request.getEmail());
            return ResponseEntity.ok(response);

        } catch (DomainException e) {
            log.error("Failed to verify OTP for email: {}", request.getEmail(), e);

            CheckResponse<VerifyOtpResponse> response = CheckResponse.<VerifyOtpResponse>builder()
                    .success(false)
                    .message(e.getMessage())
                    .build();

            HttpStatus status = HttpStatus.BAD_REQUEST;
            if (e.getMessage().contains("User not found")) {
                status = HttpStatus.NOT_FOUND;
            }

            return ResponseEntity.status(status).body(response);
        } catch (Exception e) {
            log.error("Unexpected error verifying OTP for email: {}", request.getEmail(), e);

            CheckResponse<VerifyOtpResponse> response = CheckResponse.<VerifyOtpResponse>builder()
                    .success(false)
                    .message("Internal server error")
                    .build();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // API 2: Reset password với token
    @PostMapping("/reset-password")
    public ResponseEntity<CheckResponse<Void>> resetPassword(
            @Parameter(description = "Reset password data", required = true) @Valid @RequestBody ResetPasswordRequest request,
            @RequestHeader(value = "X-Reset-Token", required = false) String resetToken) {

        log.info("Reset password request");

        // Validate token
        if (resetToken == null || resetToken.trim().isEmpty()) {
            CheckResponse<Void> response = CheckResponse.<Void>builder()
                    .success(false)
                    .message("Reset token is required")
                    .build();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        try {
            resetPasswordUseCase.execute(request, resetToken);

            CheckResponse<Void> response = CheckResponse.<Void>builder()
                    .success(true)
                    .message("Password reset successful")
                    .build();

            log.info("Password reset successful");
            return ResponseEntity.ok(response);

        } catch (DomainException e) {
            log.error("Failed to reset password: {}", e.getMessage());

            HttpStatus status = HttpStatus.BAD_REQUEST;
            if (e.getMessage().contains("Invalid or expired reset token")) {
                status = HttpStatus.UNAUTHORIZED;
            } else if (e.getMessage().contains("User not found")) {
                status = HttpStatus.NOT_FOUND;
            }

            CheckResponse<Void> response = CheckResponse.<Void>builder()
                    .success(false)
                    .message(e.getMessage())
                    .build();

            return ResponseEntity.status(status).body(response);
        } catch (Exception e) {
            log.error("Unexpected error resetting password", e);

            CheckResponse<Void> response = CheckResponse.<Void>builder()
                    .success(false)
                    .message("Internal server error")
                    .build();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Handle Notification
    @PatchMapping("/notification/enable")
    public ResponseEntity<CheckResponse<Boolean>> enable(
            @RequestHeader("Authorization") String auth) {
        UUID userId = extractUserIdFromToken(auth);
        notificationUseCase.enable(userId);
        CheckResponse<Boolean> response = CheckResponse.<Boolean>builder()
                .success(true)
                .message("Enabled")
                .build();
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/notification/disable")
    public ResponseEntity<CheckResponse<Boolean>> disable(
            @RequestHeader("Authorization") String auth) {
        UUID userId = extractUserIdFromToken(auth);
        notificationUseCase.disable(userId);

        CheckResponse<Boolean> response = CheckResponse.<Boolean>builder()
                .success(true)
                .message("Disabled")
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/notification")
    public ResponseEntity<CheckResponse<Boolean>> getStatus(
            @RequestHeader("Authorization") String auth) {
        UUID userId = extractUserIdFromToken(auth);

        Boolean status = notificationUseCase.getStatus(userId);

        CheckResponse<Boolean> response = CheckResponse.<Boolean>builder()
                .success(true)
                .data(status)
                .message("Status Notificattion")
                .build();

        return ResponseEntity.ok(response);
    }

    private String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Invalid Authorization header");
    }

    /**
     * Extract user ID from JWT token in Authorization header
     */
    private UUID extractUserIdFromToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return jwtTokenProvider.getUserIdFromToken(token);
        }
        throw new IllegalArgumentException("Invalid Authorization header");
    }

    @Operation(summary = "Get financial setup", description = "Get financial setup information of currently authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Financial setup retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/me/financial-setup")
    public ResponseEntity<CheckResponse<FinancialSetupResponse>> getFinancialSetup(
            @Parameter(hidden = true) @CurrentUser UUID userId) {
        FinancialSetupResponse financialSetup = getFinancialSetupUseCase.execute(userId);

        CheckResponse<FinancialSetupResponse> response = CheckResponse.<FinancialSetupResponse>builder()
                .success(true)
                .message("Financial setup retrieved successfully")
                .data(financialSetup)
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Create or update financial setup", description = "Create or update income, saving pace, intervention level, and focus mode of currently authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Financial setup updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid financial setup data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PutMapping("/me/financial-setup")
    public ResponseEntity<CheckResponse<FinancialSetupResponse>> updateFinancialSetup(
            @Parameter(hidden = true) @CurrentUser UUID userId,
            @Valid @RequestBody UpdateFinancialSetupRequest request) {
        FinancialSetupResponse financialSetup = updateFinancialSetupUseCase.execute(userId, request);

        CheckResponse<FinancialSetupResponse> response = CheckResponse.<FinancialSetupResponse>builder()
                .success(true)
                .message("Financial setup updated successfully")
                .data(financialSetup)
                .build();

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete financial setup", description = "Clear financial setup information of currently authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Financial setup deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @DeleteMapping("/me/financial-setup")
    public ResponseEntity<CheckResponse<FinancialSetupResponse>> deleteFinancialSetup(
            @Parameter(hidden = true) @CurrentUser UUID userId) {
        FinancialSetupResponse financialSetup = deleteFinancialSetupUseCase.execute(userId);

        CheckResponse<FinancialSetupResponse> response = CheckResponse.<FinancialSetupResponse>builder()
                .success(true)
                .message("Financial setup deleted successfully")
                .data(financialSetup)
                .build();

        return ResponseEntity.ok(response);
    }
}
