package com.example.smartmoney.domain.repositories;

import com.example.smartmoney.domain.models.notification.Notification;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface NotificationRepository {
    Notification save(Notification notification);
    int updateReadStatusByUserIdAndIds(UUID userId, List<UUID> ids, boolean read);
    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);
    void deleteExceedingByUserId(UUID userId, int max);
}