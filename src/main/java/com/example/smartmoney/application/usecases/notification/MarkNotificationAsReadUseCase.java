package com.example.smartmoney.application.usecases.notification;


import com.example.smartmoney.domain.models.notification.Notification;
import com.example.smartmoney.domain.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MarkNotificationAsReadUseCase {

    private final NotificationRepository notificationRepository;

    @Transactional
    public void markManyAsRead(List<UUID> notificationIds, UUID userId) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            return; // hoặc throw exception tùy logic
        }
        // Kiểm tra tất cả notificationIds thuộc userId và cập nhật read=true
        int updatedCount = notificationRepository.updateReadStatusByUserIdAndIds(userId, notificationIds, true);
        // Nếu cần báo lỗi khi có ID không hợp lệ, bạn có thể so sánh updatedCount với notificationIds.size()
        // và ném exception nếu không khớp.
        if (updatedCount != notificationIds.size()) {
            throw new RuntimeException("Some notifications do not belong to user or do not exist");
        }
    }
}