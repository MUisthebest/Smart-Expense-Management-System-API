package com.example.smartmoney.application.dtos.requests.project;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InviteProjectMemberRequest {
    @NotBlank(message = "UserId is required")
    private String userId;

    private boolean admin;
}
