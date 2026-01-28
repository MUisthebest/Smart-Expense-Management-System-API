package com.example.smartmoney.domain.repositories;
import com.example.smartmoney.domain.models.otp.OTP;
import com.example.smartmoney.domain.models.user.valueobjects.Email;
import java.util.Optional;

public interface OTPRepository {  
    void save(OTP otp);
    Optional<OTP> findValidByEmail(Email email);
    void deleteByEmail(Email email);
}
