package com.example.smartmoney.application.dtos.requests.auth;

import lombok.Data;
import java.time.LocalDate;

@Data
public class UpdateUserRequest {
    private String fullName;
    private String avatar;
    private LocalDate dateOfBirth;
    private String phone;
    // Note: Email update should be a separate process with verification
}