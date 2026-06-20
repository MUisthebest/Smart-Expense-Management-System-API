package com.example.smartmoney.application.dtos.requests.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeviceCodeRequest {

    @NotBlank(message = "Client ID is required")
    private String clientId;
}