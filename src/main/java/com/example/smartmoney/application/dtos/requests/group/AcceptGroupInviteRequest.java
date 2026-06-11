package com.example.smartmoney.application.dtos.requests.group;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AcceptGroupInviteRequest {
    @NotBlank(message = "Token is required")
    private String token;
}
