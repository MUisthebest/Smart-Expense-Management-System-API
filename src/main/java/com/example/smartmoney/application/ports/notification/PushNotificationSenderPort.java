package com.example.smartmoney.application.ports.notification;

public interface PushNotificationSenderPort {
    void send(String pushToken, String title, String body, Object data);
}