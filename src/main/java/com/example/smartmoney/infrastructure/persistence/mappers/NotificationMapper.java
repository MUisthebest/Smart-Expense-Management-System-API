package com.example.smartmoney.infrastructure.persistence.mappers;

import com.example.smartmoney.domain.models.notification.Notification;
import com.example.smartmoney.infrastructure.persistence.entities.NotificationEntity;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public Notification toDomain(NotificationEntity entity) {
        return new Notification(
            entity.getId(),
            entity.getUserId(),
            entity.getContent(),
            entity.getDeepLink(),
            entity.getCreatedAt(),
            entity.isRead()
        );
    }

    public NotificationEntity toEntity(Notification domain) {
        return NotificationEntity.builder()
                .userId(domain.getUserId())
                .content(domain.getContent())
                .deepLink(domain.getDeepLink())
                .createdAt(domain.getCreatedAt())
                .read(domain.getRead())
                .build();
    }
}
