package com.example.smartmoney.application.dtos.requests.notification;

import lombok.Data;

@Data
public class SavePushTokenRequest {
    private String pushToken;
    private String platform;
}