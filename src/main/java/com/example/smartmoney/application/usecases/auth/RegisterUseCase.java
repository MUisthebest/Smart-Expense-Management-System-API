package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.dtos.requests.auth.RegisterRequest;
import com.example.smartmoney.application.utils.OtpGenerator;
import com.example.smartmoney.application.ports.mail.MailPort;
import com.example.smartmoney.domain.models.otp.OTP;
import com.example.smartmoney.domain.models.user.valueobjects.*;
import com.example.smartmoney.domain.repositories.OTPRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.exceptions.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RegisterUseCase {
    
    private final OTPRepository otpRepository;
    private final UserRepository userRepository;
    private final MailPort mailPort;

    public void  execute(RegisterRequest request) {
        // 1. Validate request
        validateRequest(request);
        
        // 2. Check duplicates
        checkDuplicates(request);
        
        // 3. Send Email verification (nếu có)
        Email email = new Email(request.getEmail());
        String otp = OtpGenerator.generate(); // 6 số
        OTP otpModel = OTP.builder()
            .email(email)
            .otp(otp)
            .expiredAt(java.time.LocalDateTime.now().plusMinutes(10))
            .verified(false)
            .build();
        otpRepository.save(otpModel);
        String subject = "SmartMoney - Email Verification";
        String body = mailPort.generateVerificationEmail(email.getValue(), otp);
        mailPort.sendHtmlMail(email.getValue(), subject, body);
    }
    
    private void validateRequest(RegisterRequest request) {
        if (request == null) {
            throw new DomainException("Register request cannot be null");
        }
        
        // Các validation cơ bản
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new DomainException("Email is required");
        }
        
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new DomainException("Password is required");
        }
        
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new DomainException("Username is required");
        }
        
        // Password validation (có thể để trong Password value object)
        if (request.getPassword().length() < 6) {
            throw new DomainException("Password must be at least 6 characters");
        }

        if (request.getPassword().length() > 20) {
            throw new DomainException("Password must not exceed 20 characters");
        }
    }
    
    private void checkDuplicates(RegisterRequest request) {
        Email email = new Email(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new DomainException("Email already registered");
        }
        
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DomainException("Username already taken");
        }
        
        Phone phone = new Phone(request.getPhone());
        if (userRepository.existsByPhone(phone)) {
            throw new DomainException("Phone number already registered");
        }
    }
    
}