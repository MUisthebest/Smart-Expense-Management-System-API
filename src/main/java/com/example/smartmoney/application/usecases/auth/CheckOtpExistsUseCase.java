package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.ports.otp.OtpCachePort;
import com.example.smartmoney.domain.models.user.valueobjects.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CheckOtpExistsUseCase {

    private final OtpCachePort otpCachePort;

    public boolean execute(String email) {
        return otpCachePort.exists(new Email(email));
    }
}
