package com.example.smartmoney.application.usecases.auth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.smartmoney.application.dtos.responses.UserResponse;
import com.example.smartmoney.application.ports.auth.AuthPort;
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
    private final AuthPort authPort;

    public UserResponse execute(VerifyEmailRequest request) {

        Email email = new Email(request.getEmail());

        OTP otp = otpRepository
            .findValidByEmail(email)
            .orElseThrow(() -> new DomainException("OTP not found"));

        otp.verify(request.getOtp());

        // check duplicate lần cuối (important)
        if (userRepository.existsByEmail(email)) {
            throw new DomainException("Email already registered");
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
        otpRepository.deleteByEmail(email);

        return mapToUserResponse(savedUser);
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
