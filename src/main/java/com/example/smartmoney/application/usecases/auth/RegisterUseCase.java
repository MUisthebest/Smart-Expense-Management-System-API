package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.dtos.requests.auth.RegisterRequest;
import com.example.smartmoney.application.ports.cloudinary.CloudinaryPort;
import com.example.smartmoney.application.dtos.responses.UserResponse;
import com.example.smartmoney.application.utils.OtpGenerator;
import com.example.smartmoney.application.ports.mail.MailPort;
import com.example.smartmoney.application.ports.otp.OtpCachePort;
import com.example.smartmoney.domain.models.otp.OTP;
import com.example.smartmoney.domain.models.otp.enums.Type;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.valueobjects.*;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.exceptions.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.smartmoney.application.ports.auth.AuthPort;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RegisterUseCase {
    
    private final OtpCachePort otpCachePort;  
    private final CloudinaryPort cloudinaryPort;
    private final UserRepository userRepository;
    private final MailPort mailPort;
    private final AuthPort authPort;

    public UserResponse execute(RegisterRequest request) {
        // 1. Validate request
        validateRequest(request);
        
        // 2. Xử lý email
        Email email = new Email(request.getEmail());

        // 3. Kiểm tra email duplicate
        Optional<User> existingUserByEmail = userRepository.findByEmail(email);
        if (existingUserByEmail.isPresent()) {
            handleDuplicateUser(existingUserByEmail.get(), "Email");
        }

        // 4. Kiểm tra username duplicate
        Optional<User> existingUserByUsername = userRepository.findByUsername(request.getUsername());
        if (existingUserByUsername.isPresent()) {
            handleDuplicateUser(existingUserByUsername.get(), "Username");
        }

        // 5. Kiểm tra phone duplicate nếu có
        if (request.getPhone() != null) {
            Phone phone = new Phone(request.getPhone());
            Optional<User> existingUserByPhone = userRepository.findByPhone(phone);
            if (existingUserByPhone.isPresent()) {
                handleDuplicateUser(existingUserByPhone.get(), "Phone number");
            }
        }

        String avatarUrl = "https://res.cloudinary.com/drjlezbo7/image/upload/v1781800787/smart-money/users/avatar_0a3ad74f-f949-4ca5-a885-5aef16a2b1b1_1781800787245.png";

        if (request.getAvatar() != null && !request.getAvatar().isEmpty()) {
            avatarUrl = cloudinaryPort.uploadAvatar(request.getAvatar());
        }

        // 6. Tạo user mới
        User user = User.create(
            request.getUsername(),
            request.getFullName(),
            new Avatar(avatarUrl),
            new Password(authPort.encodePassword(request.getPassword())),
            request.getDateOfBirth() != null ? new DateOfBirth(request.getDateOfBirth()) : null,
            request.getPhone() != null ? new Phone(request.getPhone()) : null,
            email
        );

        User savedUser = userRepository.save(user);

        // 7. Tạo và gửi OTP
        try {
            String otp = OtpGenerator.generate();
            OTP otpModel = OTP.builder()
                .email(email)
                .otp(otp)
                .expiredAt(java.time.LocalDateTime.now().plusMinutes(10))
                .verified(false)
                .type(Type.VERIFY)
                .build();
            otpCachePort.save(otpModel);
            
            String subject = "SmartMoney - Email Verification";
            String body = mailPort.generateVerificationEmail(email.getValue(), otp);
            mailPort.sendHtmlMail(email.getValue(), subject, body);
        } catch (Exception e) {
            log.error("Failed to save OTP to Redis for email: {}", email.getValue(), e);
            throw new DomainException("Unable to generate verification code. Please try again later.");
        }
        return mapToUserResponse(savedUser);
    }
    
    private void validateRequest(RegisterRequest request) {
        if (request == null) {
            throw new DomainException("Register request cannot be null");
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new DomainException("Email is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new DomainException("Password is required");
        }
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new DomainException("Username is required");
        }
        if (request.getPassword().length() < 6) {
            throw new DomainException("Password must be at least 6 characters");
        }
        if (request.getPassword().length() > 20) {
            throw new DomainException("Password must not exceed 20 characters");
        }
    }
    
    private void handleDuplicateUser(User existingUser, String fieldName) {
        if (existingUser.isEmailVerified()) {
            throw new DomainException(fieldName + " already registered");
        }
        userRepository.deleteByEmail(existingUser.getEmail());
        
        try {
            otpCachePort.deleteByEmail(existingUser.getEmail());
            if (existingUser.getAvatar() != null && existingUser.getAvatar().getUrl() != null) {
                cloudinaryPort.deleteImage(existingUser.getAvatar().getUrl(), "image");
            }
            log.info("Deleted OTP for unverified user: {}", existingUser.getEmail().getValue());
        } catch (Exception e) {
            log.error("Failed to delete OTP for email {}: {}", existingUser.getEmail().getValue(), e.getMessage());
        }
        
        log.info("Deleted unverified user with {}: {} due to duplicate registration", 
                fieldName, existingUser.getEmail().getValue());
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
        response.setEmail(user.getEmail() != null ? user.getEmail().getValue() : null);
        response.setCoin(user.getCoin());
        response.setRole(user.getRole());
        response.setRate(user.getRate());
        response.setActive(user.isActive());
        // Disabled: income/financial setup flags removed from User (user_income / user_financial_profile tables removed).
        // response.setIncomeSetupCompleted(user.isIncomeSetupCompleted());
        // response.setFinancialSetupCompleted(user.isFinancialSetupCompleted());
        // response.setOnboardingCompleted(user.isIncomeSetupCompleted() && user.isFinancialSetupCompleted());
        return response;
    }

}
