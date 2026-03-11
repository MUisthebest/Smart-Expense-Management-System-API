package com.example.smartmoney.application.dtos.requests.auth;

import org.springframework.web.multipart.MultipartFile;
import lombok.Data;
import java.time.LocalDate;

@Data
public class UpdateUserRequest {
    private String fullName;
    private MultipartFile avatar;
    private LocalDate dateOfBirth;
    private String phone;
    // Note: Email update should be a separate process with verification
}