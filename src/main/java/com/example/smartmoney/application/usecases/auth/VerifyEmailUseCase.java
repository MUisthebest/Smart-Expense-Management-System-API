package com.example.smartmoney.application.usecases.auth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.smartmoney.application.dtos.responses.UserResponse;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.repositories.OTPRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.application.dtos.requests.auth.VerifyEmailRequest;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.otp.OTP;
import com.example.smartmoney.domain.models.user.valueobjects.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class VerifyEmailUseCase {

    private final OTPRepository otpRepository;
    private final UserRepository userRepository;

    public void execute(VerifyEmailRequest request) {

        Email email = new Email(request.getEmail());

        OTP otp = otpRepository
            .findValidByEmail(email)
            .orElseThrow(() -> new DomainException("OTP not found or expired"));

        // Verify OTP code
        otp.verify(request.getOtp());

        // Find user and mark email as verified
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new DomainException("User not found"));
        
        user.verifyEmail();
        userRepository.save(user);

        // Delete OTP after successful verification
        otpRepository.deleteByEmail(email);
        
        log.info("✅ Email verified successfully for user: {}", email.getValue());

    }

}
