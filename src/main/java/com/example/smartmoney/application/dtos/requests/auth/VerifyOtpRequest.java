// DTO cho API xác thực OTP
package com.example.smartmoney.application.dtos.requests.auth;

import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class VerifyOtpRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
    
    @NotBlank(message = "OTP is required")
    private String otp;
}