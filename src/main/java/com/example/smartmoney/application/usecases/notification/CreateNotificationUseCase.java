package com.example.smartmoney.application.usecases.notification;

import com.example.smartmoney.application.ports.notification.NotificationQueuePort;
import com.example.smartmoney.domain.models.notification.Notification;
import com.example.smartmoney.domain.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CreateNotificationUseCase {

    private final NotificationRepository notificationRepository;
    private final NotificationQueuePort queuePort;

    @Transactional
    public Notification createNotification(UUID userId, String content) {

        Notification notification =
            new Notification(userId, content, LocalDateTime.now());

        Notification saved = notificationRepository.save(notification);

        // ✅ đẩy sang queue (qua port)
        queuePort.enqueue(saved);

        return saved;
    }
}