package com.example.smartmoney.application.usecases.notification;

import com.example.smartmoney.application.ports.notification.NotificationQueuePort;
import com.example.smartmoney.domain.models.notification.Notification;
import com.example.smartmoney.domain.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreateNotificationUseCase {

    private final NotificationRepository notificationRepository;
    private final NotificationQueuePort queuePort;

    @Transactional
    public Notification createNotification(UUID userId, String content) {
        return createNotification(userId, content, null);
    }

    @Transactional
    public Notification createNotification(UUID userId, String content, String deepLink) {
        Notification notification = new Notification(userId, content, deepLink, LocalDateTime.now());
        Notification saved = notificationRepository.save(notification);
        // The persisted notification is the source of truth; the queue is best-effort realtime delivery.
        // A queue/Redis outage must not roll back the saved row.
        try {
            queuePort.enqueue(saved);
        } catch (Exception e) {
            log.warn("[Notification] Enqueue failed for {} (saved id={}): {}",
                    userId, saved.getId(), e.getMessage());
        }
        return saved;
    }
}