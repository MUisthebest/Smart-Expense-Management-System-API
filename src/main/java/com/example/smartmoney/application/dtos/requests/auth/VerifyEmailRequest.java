package com.example.smartmoney.application.dtos.requests.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class VerifyEmailRequest {
    @NotBlank(message = "Username is required")
    @Size(max = 20, message = "Username must not exceed 20 characters")
    private String username;
    
    @NotBlank(message = "Full name is required")
    @Size(max = 40, message = "Full name must not exceed 40 characters")
    private String fullName;
    
    private String avatar; // Optional
    
    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 20, message = "Password must be 6-20 characters")
    private String password;
    
    @NotBlank(message = "Date of birth is required")
    @Pattern(regexp = "\\d{2}/\\d{2}/\\d{4}", message = "Date must be in dd/MM/yyyy format")
    private String dateOfBirth;
    
    @NotBlank(message = "Phone is required")
    @Size(max = 20, message = "Phone must not exceed 20 digits")
    @Pattern(regexp = "\\d+", message = "Phone must contain only digits")
    private String phone;
    
    @NotBlank(message = "Email is required")
    @Size(max = 30, message = "Email must not exceed 30 characters")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank
    private String otp;
}
