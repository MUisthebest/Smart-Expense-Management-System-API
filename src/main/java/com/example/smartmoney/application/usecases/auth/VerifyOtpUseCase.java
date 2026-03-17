package com.example.smartmoney.application.usecases.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.smartmoney.application.dtos.requests.auth.VerifyOtpRequest;
import com.example.smartmoney.application.dtos.responses.VerifyOtpResponse;
import com.example.smartmoney.application.ports.auth.AuthPort;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.repositories.OTPRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.models.user.valueobjects.Email;
import com.example.smartmoney.domain.models.otp.OTP;
import com.example.smartmoney.domain.models.user.User;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class VerifyOtpUseCase {

    private final OTPRepository otpRepository;
    private final UserRepository userRepository;
    private final AuthPort authPort;

    public VerifyOtpResponse execute(VerifyOtpRequest request) {
        Email email = new Email(request.getEmail());

        // Kiểm tra user tồn tại
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new DomainException("User not found"));

        // Tìm và validate OTP
        OTP otp = otpRepository.findValidByEmail(email)
                .orElseThrow(() -> new DomainException("Invalid or expired OTP"));

        // Verify OTP
        otp.verify(request.getOtp());

        // Tạo reset token sử dụng AuthPort
        String resetToken = authPort.generateResetToken(email.getValue());

        // Xóa OTP sau khi verify thành công
        otpRepository.deleteByEmail(email);

        log.info("OTP verified successfully for email: {}", email.getValue());

        return VerifyOtpResponse.builder()
                .resetToken(resetToken)
                .email(email.getValue())
                .build();
    }
}