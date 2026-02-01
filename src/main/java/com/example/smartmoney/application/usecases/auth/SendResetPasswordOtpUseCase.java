package com.example.smartmoney.application.usecases.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.smartmoney.application.dtos.requests.auth.SendResetPasswordOtpRequest;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.repositories.OTPRepository;
import com.example.smartmoney.domain.models.otp.OTP;
import com.example.smartmoney.domain.models.otp.enums.Type;
import com.example.smartmoney.domain.models.user.valueobjects.Email;
import com.example.smartmoney.application.ports.mail.MailPort;
import com.example.smartmoney.application.utils.OtpGenerator;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SendResetPasswordOtpUseCase {

    private final UserRepository userRepository;
    private final OTPRepository otpRepository;
    private final MailPort mailPort;

    public void execute(SendResetPasswordOtpRequest request) {
        Email email = new Email(request.getEmail());
        
        // Check if user exists with this email
        if (!userRepository.existsByEmail(email)) {
            // For security reasons, don't reveal if email exists or not
            log.warn("Reset password attempt for non-existent email: {}", request.getEmail());
            return; // Silently return without error
        }

        // Delete any existing OTP for this email
        otpRepository.deleteByEmail(email);

        // Generate new OTP
        String otp = OtpGenerator.generate(); // 6 số
        OTP otpModel = OTP.builder()
            .email(email)
            .otp(otp)
            .expiredAt(java.time.LocalDateTime.now().plusMinutes(10))
            .verified(false)
            .type(Type.UPDATE)
            .build();
        otpRepository.save(otpModel);
        String subject = "SmartMoney - Email Verification";
        String body = mailPort.generateVerificationEmail(email.getValue(), otp);
        mailPort.sendHtmlMail(email.getValue(), subject, body);
    }
}