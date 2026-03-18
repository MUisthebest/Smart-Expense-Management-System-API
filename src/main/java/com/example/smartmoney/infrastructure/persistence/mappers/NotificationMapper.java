package com.example.smartmoney.infrastructure.persistence.mappers;

import com.example.smartmoney.domain.models.notification.Notification;
import com.example.smartmoney.infrastructure.persistence.entities.NotificationEntity;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public static NotificationEntity toEntity(Notification domain) {
        if (domain == null) return null;
        return NotificationEntity.builder()
                .id(domain.getId())
                .userId(domain.getUserId())
                .content(domain.getContent())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    public static Notification toDomain(NotificationEntity entity) {
        if (entity == null) return null;
        Notification notification = new Notification();
        notification.setId(entity.getId());
        notification.setUserId(entity.getUserId());
        notification.setContent(entity.getContent());
        notification.setCreatedAt(entity.getCreatedAt());
        return notification;
    }
}