package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.requests.auth.LoginRequest;
import com.example.smartmoney.application.dtos.requests.auth.RefreshTokenRequest;
import com.example.smartmoney.application.dtos.requests.auth.RegisterRequest;
import com.example.smartmoney.application.dtos.requests.auth.GoogleLoginRequest;
import com.example.smartmoney.application.dtos.requests.auth.FacebookLoginRequest;
import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.application.dtos.responses.UserResponse;
import com.example.smartmoney.application.dtos.responses.AuthResponse;
import com.example.smartmoney.application.usecases.auth.LoginUseCase;
import com.example.smartmoney.application.usecases.auth.RefreshTokenUseCase;
import com.example.smartmoney.application.usecases.auth.RegisterUseCase;
import com.example.smartmoney.application.usecases.auth.GoogleLoginUseCase;
import com.example.smartmoney.application.usecases.auth.FacebookLoginUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "Authentication",
    description = "Authentication APIs for user registration, login, and token management"
)
public class AuthController {
    
    private final RegisterUseCase registerUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final GoogleLoginUseCase googleLoginUseCase;
    private final FacebookLoginUseCase facebookLoginUseCase;
    
    @Operation(
        summary = "Register new user",
        description = "Register a new user with required information"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "User registered successfully",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid input or user already exists",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error"
        )
    })
    @PostMapping("/register")
    public ResponseEntity<CheckResponse<UserResponse>> register(
            @Parameter(description = "User registration data", required = true)
            @Valid @RequestBody RegisterRequest request) {
        
        log.info("Register request for email: {}", request.getEmail());
        
        try {
            UserResponse registerResponse = registerUseCase.execute(request);
            
            CheckResponse<UserResponse> response = CheckResponse.<UserResponse>builder()
                .success(true)
                .message("User registered successfully")
                .data(registerResponse)
                .build();
            
            log.info("User registered successfully: {}", request.getEmail());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Registration failed for email: {}", request.getEmail(), e);
            
            CheckResponse<UserResponse> response = CheckResponse.<UserResponse>builder()
                .success(false)
                .message("Registration failed: " + e.getMessage())
                .build();
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
    
    @Operation(
        summary = "User login",
        description = "Authenticate user with email and password"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Login successful",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Invalid credentials",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid input"
        )
    })
    @PostMapping("/login")
    public ResponseEntity<CheckResponse<AuthResponse>> login(
            @Parameter(description = "User login credentials", required = true)
            @Valid @RequestBody LoginRequest request) {
        
        log.info("Login attempt for email: {}", request.getEmail());
        
        try {
            AuthResponse authResponse = loginUseCase.execute(request);
            
            CheckResponse<AuthResponse> response = CheckResponse.<AuthResponse>builder()
                .success(true)
                .message("Login successful")
                .data(authResponse)
                .build();
            
            log.info("Login successful for email: {}", request.getEmail());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.warn("Login failed for email: {}", request.getEmail(), e);
            
            CheckResponse<AuthResponse> response = CheckResponse.<AuthResponse>builder()
                .success(false)
                .message("Login failed: " + e.getMessage())
                .build();
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }
    
    @Operation(
        summary = "Google login",
        description = "Authenticate user with Google ID token"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Google login successful",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Invalid or expired Google token"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid input"
        )
    })
    @PostMapping("/login/google")
    public ResponseEntity<CheckResponse<AuthResponse>> googleLogin(
            @Parameter(description = "Google login request with idToken", required = true)
            @Valid @RequestBody GoogleLoginRequest request) {
        
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
    
    @Operation(
        summary = "Facebook login",
        description = "Authenticate user with Facebook access token"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Facebook login successful",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Invalid or expired Facebook token"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid input"
        )
    })
    @PostMapping("/login/facebook")
    public ResponseEntity<CheckResponse<AuthResponse>> facebookLogin(
            @Parameter(description = "Facebook login request with accessToken", required = true)
            @Valid @RequestBody FacebookLoginRequest request) {
        
        log.info("Facebook login attempt");
        
        try {
            AuthResponse authResponse = facebookLoginUseCase.execute(request);
            
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
    
    @Operation(
        summary = "Refresh access token",
        description = "Get new access token using refresh token"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Token refreshed successfully",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Invalid or expired refresh token"
        )
    })
    @PostMapping("/refresh-token")
    public ResponseEntity<CheckResponse<AuthResponse>> refreshToken(
            @Parameter(description = "Refresh token request", required = true)
            @Valid @RequestBody RefreshTokenRequest request) {
        
        log.debug("Refresh token request");
        
        try {
            AuthResponse authResponse = refreshTokenUseCase.execute(request);
            
            CheckResponse<AuthResponse> response = CheckResponse.<AuthResponse>builder()
                .success(true)
                .message("Token refreshed successfully")
                .data(authResponse)
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.warn("Refresh token failed", e);
            
            CheckResponse<AuthResponse> response = CheckResponse.<AuthResponse>builder()
                .success(false)
                .message("Refresh token failed: " + e.getMessage())
                .build();
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }
    
    @Operation(
        summary = "User logout",
        description = "Logout user and invalidate refresh token"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Logout successful"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized"
        )
    })
    @PostMapping("/logout")
    public ResponseEntity<CheckResponse<Void>> logout(
            @Parameter(description = "Bearer token", required = true)
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            // Extract token from header
            String token = extractTokenFromHeader(authHeader);
            
            // Implementation: Invalidate token (add to blacklist, etc.)
            // For now, just return success
            
            CheckResponse<Void> response = CheckResponse.<Void>builder()
                .success(true)
                .message("Logout successful")
                .build();
            
            log.info("User logged out successfully");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            CheckResponse<Void> response = CheckResponse.<Void>builder()
                .success(false)
                .message("Logout failed: " + e.getMessage())
                .build();
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }
    
    @Operation(
        summary = "Health check",
        description = "Check if authentication service is running"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Service is healthy"
        )
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
    
    @Operation(
        summary = "Get current user info",
        description = "Get information of currently authenticated user"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "User information retrieved"
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized"
        )
    })
    @GetMapping("/me")
    public ResponseEntity<CheckResponse<String>> getCurrentUser(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader) {
        
        // Implementation would extract user info from token
        CheckResponse<String> response = CheckResponse.<String>builder()
            .success(true)
            .message("Current user endpoint")
            .data("User info would be here")
            .build();
        
        return ResponseEntity.ok(response);
    }
    
    private String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Invalid Authorization header");
    }
}