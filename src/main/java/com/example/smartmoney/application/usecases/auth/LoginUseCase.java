package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.dtos.requests.auth.LoginRequest;
import com.example.smartmoney.application.dtos.responses.AuthResponse;
import com.example.smartmoney.application.dtos.responses.UserResponse;
import com.example.smartmoney.application.ports.auth.AuthPort;
import com.example.smartmoney.domain.models.otp.OTP;
import com.example.smartmoney.domain.models.user.*;
import com.example.smartmoney.domain.models.user.valueobjects.*;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.repositories.OTPRepository;
import com.example.smartmoney.domain.exceptions.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LoginUseCase {
    
    private final UserRepository userRepository;
    private final OTPRepository otpRepository;
    private final AuthPort authPort;
    
    public LoginUseCase(UserRepository userRepository, OTPRepository otpRepository, AuthPort authPort) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.authPort = authPort;
    }
    
    public AuthResponse execute(LoginRequest request) {
        Email email = new Email(request.getEmail());

        // Find user by email
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new DomainException("Invalid email"));

        boolean hasActiveOTP = otpRepository.existsValidByEmail(email);
        if (hasActiveOTP) {
            throw new DomainException("Please verify your email first");
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
        
        // Update refresh token in database
        user.updateRefreshToken(new RefreshToken(refreshToken, java.time.LocalDateTime.now().plusDays(7)));
        userRepository.save(user);
        
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
        response.setDateOfBirth(user.getDateOfBirth().getFormattedValue());
        response.setPhone(user.getPhone().getValue());
        response.setEmail(user.getEmail().getValue());
        response.setCoin(user.getCoin());
        response.setRole(user.getRole());
        response.setRate(user.getRate());
        response.setActive(user.isActive());
        return response;
    }
}