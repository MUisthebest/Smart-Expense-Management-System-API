package com.example.smartmoney.application.usecases.auth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.smartmoney.application.dtos.requests.auth.ResetPasswordRequest;
import com.example.smartmoney.application.ports.auth.AuthPort;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.repositories.OTPRepository;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.otp.OTP;
import com.example.smartmoney.domain.models.user.valueobjects.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ResetPasswordUseCase {

    private final UserRepository userRepository;
    private final OTPRepository otpRepository;
    private final AuthPort authPort;

    public void execute(ResetPasswordRequest request) {
        Email email = new Email(request.getEmail());

        // Find user
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new DomainException("User not found"));

        // Find and validate OTP
        OTP otp = otpRepository.findValidByEmail(email)
            .orElseThrow(() -> new DomainException("Invalid or expired OTP"));

        otp.verify(request.getOtp());

        // Update password
        Password newPassword = new Password(authPort.encodePassword(request.getNewPassword()));
        user.setPassword(newPassword);

        userRepository.save(user);
        
        // Delete used OTP
        otpRepository.deleteByEmail(email);

    }
}