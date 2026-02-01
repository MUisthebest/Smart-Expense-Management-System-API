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

    public void execute(VerifyEmailRequest request) {

        Email email = new Email(request.getEmail());

        OTP otp = otpRepository
            .findValidByEmail(email)
            .orElseThrow(() -> new DomainException("OTP not found"));

        otp.verify(request.getOtp());

        otpRepository.deleteByEmail(email);

    }

}
