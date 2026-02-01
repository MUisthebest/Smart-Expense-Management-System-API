// Response cho verify OTP
package com.example.smartmoney.application.dtos.responses;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VerifyOtpResponse {
    private String resetToken;
    private String email;
}