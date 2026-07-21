// application/ports/otp/OtpCachePort.java
package com.example.smartmoney.application.ports.otp;

import com.example.smartmoney.domain.models.otp.OTP;
import com.example.smartmoney.domain.models.user.valueobjects.Email;

public interface OtpCachePort {
    void save(OTP otp);
    boolean verify(Email email, String rawOtp);
    boolean exists(Email email);
    void deleteByEmail(Email email);
}