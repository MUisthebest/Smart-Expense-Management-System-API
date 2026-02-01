package com.example.smartmoney.application.usecases.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.smartmoney.application.dtos.requests.auth.ResetPasswordRequest;
import com.example.smartmoney.application.ports.auth.AuthPort;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.valueobjects.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ResetPasswordUseCase {

    private final UserRepository userRepository;
    private final AuthPort authPort;

    public void execute(ResetPasswordRequest request, String resetToken) {
        // Validate reset token và lấy email từ token
        String email = authPort.validateResetToken(resetToken);
        
        if (email == null || email.trim().isEmpty()) {
            throw new DomainException("Invalid or expired reset token");
        }

        Email userEmail = new Email(email);
        
        // Find user
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new DomainException("User not found"));

        // Update password
        Password newPassword = new Password(authPort.encodePassword(request.getNewPassword()));
        user.setPassword(newPassword);

        userRepository.save(user);
        
        // Invalidate token sau khi sử dụng
        authPort.invalidateResetToken(resetToken);

        log.info("Password reset successful for email: {}", email);
    }
}