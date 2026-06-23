package com.example.smartmoney.application.dtos.requests.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeviceTokenRequest {
    @NotBlank(message = "Device code is required")
    private String deviceCode;

    @NotBlank(message = "Client ID is required")
    private String clientId;
}
