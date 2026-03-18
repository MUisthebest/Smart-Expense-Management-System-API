package com.example.smartmoney.domain.repositories;

import com.example.smartmoney.domain.models.notification.Notification;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository {
    Notification save(Notification notification);
    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);
    long count();
    // Tìm thời điểm (createdAt) nhỏ nhất trong số limit bản ghi mới nhất
    Optional<LocalDateTime> findMinCreatedAtInLatestNotifications(int limit);
    // Xóa các bản ghi có createdAt trước thời điểm cho trước
    void deleteOlderThan(LocalDateTime threshold);
}