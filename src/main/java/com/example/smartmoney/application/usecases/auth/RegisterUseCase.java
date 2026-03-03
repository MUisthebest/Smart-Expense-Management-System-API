package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.dtos.requests.auth.RegisterRequest;
import com.example.smartmoney.application.dtos.responses.UserResponse;
import com.example.smartmoney.application.utils.OtpGenerator;
import com.example.smartmoney.application.ports.mail.MailPort;
import com.example.smartmoney.domain.models.otp.OTP;
import com.example.smartmoney.domain.models.otp.enums.Type;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.valueobjects.*;
import com.example.smartmoney.domain.repositories.OTPRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.exceptions.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.smartmoney.application.ports.auth.AuthPort;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RegisterUseCase {
    
    private final OTPRepository otpRepository;
    private final UserRepository userRepository;
    private final MailPort mailPort;
    private final AuthPort authPort;

    public UserResponse execute(RegisterRequest request) {
        // 1. Validate request
        validateRequest(request);
        
        // 2. Check duplicates
        checkDuplicates(request);
        
        // 3. Send Email verification (nếu có)
        Email email = new Email(request.getEmail());

        // check duplicate lần cuối (important)
        if (userRepository.existsByEmail(email)) {
            if (!userRepository.isVerifiedByEmail(email)) {
                // Nếu email đã tồn tại nhưng chưa được xác thực, xóa user cũ và OTP liên quan để tạo mới
                userRepository.deleteByEmail(email);
                otpRepository.deleteByEmail(email);
            } else {
                throw new DomainException("Email already registered");
            }
        }

        User user = User.create(
            request.getUsername(),
            request.getFullName(),
            new Avatar(request.getAvatar()),
            new Password(authPort.encodePassword(request.getPassword())),
            request.getDateOfBirth() != null ? new DateOfBirth(request.getDateOfBirth()) : null,
            request.getPhone() != null ? new Phone(request.getPhone()) : null,
            email
        );

        User savedUser = userRepository.save(user);

        String otp = OtpGenerator.generate(); // 6 số
        OTP otpModel = OTP.builder()
            .email(email)
            .otp(otp)
            .expiredAt(java.time.LocalDateTime.now().plusMinutes(10))
            .verified(false)
            .type(Type.VERIFY)
            .build();
        otpRepository.save(otpModel);
        String subject = "SmartMoney - Email Verification";
        String body = mailPort.generateVerificationEmail(email.getValue(), otp);
        mailPort.sendHtmlMail(email.getValue(), subject, body);

        return mapToUserResponse(savedUser);
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

    private UserResponse mapToUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setFullName(user.getFullName());
        response.setAvatar(user.getAvatar() != null ? user.getAvatar().getUrl() : null);
        response.setDateOfBirth(user.getDateOfBirth() != null ? 
            user.getDateOfBirth().getFormattedValue() : null);
        response.setPhone(user.getPhone() != null ? user.getPhone().getValue() : null);
        response.setEmail(user.getEmail().getValue());
        response.setCoin(user.getCoin());
        response.setRole(user.getRole());
        response.setRate(user.getRate());
        response.setActive(user.isActive());
        return response;
    }
    
}