package com.example.smartmoney.infrastructure.notification.adapters;

import com.example.smartmoney.application.ports.notification.PushNotificationSenderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ExpoPushNotificationSender implements PushNotificationSenderPort {

    @Qualifier("coreRedisTemplate")
    private final RestTemplate restTemplate;

    private static final String EXPO_URL = "https://exp.host/--/api/v2/push/send";

    @Override
    public void send(String pushToken, String title, String body, Object data) {

        Map<String, Object> payload = Map.of(
                "to", pushToken,
                "title", title,
                "body", body,
                "data", data
        );

        try {
            restTemplate.postForObject(EXPO_URL, payload, String.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}