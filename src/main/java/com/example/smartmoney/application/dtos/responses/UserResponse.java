package com.example.smartmoney.application.dtos.responses;

import com.example.smartmoney.domain.models.user.enums.Role;
import lombok.Data;
import lombok.*;
import java.util.UUID;

@Data
public class UserResponse {
    private UUID id;
    private String username;
    private String fullName;
    private String avatar;
    private String dateOfBirth;
    private String phone;
    private String email;
    private Integer coin;
    private Role role;
    private Double rate;
    private boolean active;
}