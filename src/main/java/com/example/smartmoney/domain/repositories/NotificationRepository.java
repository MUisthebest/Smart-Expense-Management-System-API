package com.example.smartmoney.domain.repositories;

import com.example.smartmoney.domain.models.notification.Notification;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository {
    Notification save(Notification notification);
    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);
    void deleteExceedingByUserId(UUID userId, int max);
}