package com.example.smartmoney.application.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.smartmoney.domain.models.notification.Notification;
import com.example.smartmoney.domain.repositories.NotificationRepository;
import com.example.smartmoney.application.ports.notification.NotificationPublisherPort;
import com.example.smartmoney.application.ports.notification.PushNotificationSenderPort;
import com.example.smartmoney.application.ports.notification.PushTokenPort;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPublisherPort publisher;
    private final PushNotificationSenderPort pushSender;
    private final PushTokenPort pushTokenRepo;

    @Transactional
    public void process(Notification notification, int max) {
        notificationRepository.deleteExceedingByUserId(
            notification.getUserId(), max
        );

        publisher.publish(notification);

        var tokens = pushTokenRepo.findByUserId(notification.getUserId().toString());

        for (var pushToken : tokens) {
            pushSender.send(
                pushToken.getPushToken(),
                "New Notification",
                notification.getContent(),
                Map.of("notificationId", notification.getId().toString())
            );
        }
    }
}