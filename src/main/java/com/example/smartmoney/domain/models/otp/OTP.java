package com.example.smartmoney.domain.models.otp;
import com.example.smartmoney.domain.models.user.valueobjects.Email;
import com.example.smartmoney.domain.models.otp.enums.Type;
import java.time.LocalDateTime;

import com.example.smartmoney.domain.exceptions.DomainException;

import lombok.*;

@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Setter
public class OTP {
    private Long id;
    private Email email;
    private String otp;
    private LocalDateTime expiredAt;
    private boolean verified;
    private Type type;

    public static OTP create(Email email, String otp, Type type ) {
        OTP obj = new OTP();
        obj.setEmail(email);
        obj.setOtp(otp);
        obj.setExpiredAt(LocalDateTime.now().plusMinutes(10));
        obj.setVerified(false);
        obj.setType(type);
        return obj;
    }

    public Email getEmail() {
        return email;
    }

    public void setEmail(Email email) {
        this.email = email;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }
    public void setType(Type type) {
        this.type = type;
    }
    public Type getType(){
        return type;
    }
    public LocalDateTime getExpiredAt() {
        return expiredAt;
    }
    public void setExpiredAt(LocalDateTime expiredAt) {
        this.expiredAt = expiredAt;
    }
    public boolean isVerified() {
        return verified;
    }
    public void setVerified(boolean verified) {
        this.verified = verified;
    }
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiredAt);
    }
    public void verify(String otpInput) {
        if (verified) throw new DomainException("Email already verified");
        if (isExpired()) throw new DomainException("OTP expired");
        if (!this.otp.equals(otpInput)) throw new DomainException("Invalid OTP");
        this.verified = true;
    }
}