package com.example.smartmoney.domain.repositories;

import com.example.smartmoney.domain.models.notification.Notification;
import java.time.LocalDateTime;
import java.util.Optional;

public interface NotificationRepository {
    Notification save(Notification notification);
    long count();
    // Tìm thời điểm (createdAt) nhỏ nhất trong số limit bản ghi mới nhất
    Optional<LocalDateTime> findMinCreatedAtInLatestNotifications(int limit);
    // Xóa các bản ghi có createdAt trước thời điểm cho trước
    void deleteOlderThan(LocalDateTime threshold);
}