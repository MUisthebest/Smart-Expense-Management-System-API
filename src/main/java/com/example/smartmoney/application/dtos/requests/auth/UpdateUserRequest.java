package com.example.smartmoney.application.dtos.requests.auth;

import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class UpdateUserRequest {

    @Size(max = 40, message = "Full name must not exceed 40 characters")
    private String fullName;

    private MultipartFile avatar; // Optional

    @Pattern(regexp = "\\d{2}/\\d{2}/\\d{4}", message = "Date must be in dd/MM/yyyy format")
    private String dateOfBirth;

    @Size(max = 20, message = "Phone must not exceed 20 digits")
    @Pattern(regexp = "\\d+", message = "Phone must contain only digits")
    private String phone;
}