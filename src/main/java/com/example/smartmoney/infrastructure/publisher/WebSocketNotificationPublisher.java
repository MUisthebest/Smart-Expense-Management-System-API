package com.example.smartmoney.infrastructure.publisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.example.smartmoney.application.ports.notification.NotificationPublisherPort;
import com.example.smartmoney.domain.models.notification.Notification;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WebSocketNotificationPublisher implements NotificationPublisherPort {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void publish(Notification notification) {
        messagingTemplate.convertAndSend(
            "/topic/notifications/" + notification.getUserId(),
            notification
        );
    }
}