package com.example.smartmoney.application.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.smartmoney.domain.models.notification.Notification;
import com.example.smartmoney.domain.repositories.NotificationRepository;
import com.example.smartmoney.application.ports.notification.NotificationPublisherPort;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPublisherPort publisher;

    @Transactional
    public void process(Notification notification, int max) {
        notificationRepository.deleteExceedingByUserId(
            notification.getUserId(), max
        );

        publisher.publish(notification);
    }
}