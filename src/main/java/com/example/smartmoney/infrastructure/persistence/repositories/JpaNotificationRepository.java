package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.infrastructure.persistence.entities.NotificationEntity;
import com.example.smartmoney.domain.models.notification.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface JpaNotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    // Lưu notification
    Notification save(Notification notification);

    // Đếm tổng số notification
    long count();

    List<NotificationEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    // Tìm created_at nhỏ nhất trong số limit bản ghi mới nhất (sắp xếp giảm dần theo thời gian)
    @Query(value = "SELECT MIN(created_at) FROM (SELECT created_at FROM notifications ORDER BY created_at DESC LIMIT :limit) AS top", nativeQuery = true)
    Optional<LocalDateTime> findMinCreatedAtInLatestNotifications(@Param("limit") int limit);

    // Xóa các bản ghi có created_at trước threshold
    void deleteByCreatedAtBefore(LocalDateTime threshold);
}