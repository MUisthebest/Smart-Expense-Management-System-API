package com.example.smartmoney.infrastructure.persistence.mappers;

import com.example.smartmoney.infrastructure.persistence.entities.PushTokenEntity;
import com.example.smartmoney.domain.models.notification.PushToken;
public class PushTokenMapper {

    public static PushToken toDomain(PushTokenEntity entity) {
        return PushToken.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .pushToken(entity.getPushToken())
                .platform(entity.getPlatform())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static PushTokenEntity toEntity(PushToken model) {
        return PushTokenEntity.builder()
                .id(model.getId())
                .userId(model.getUserId())
                .pushToken(model.getPushToken())
                .platform(model.getPlatform())
                .createdAt(model.getCreatedAt())
                .updatedAt(model.getUpdatedAt())
                .build();
    }
}