package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.domain.models.notification.Notification;
import com.example.smartmoney.infrastructure.persistence.entities.NotificationEntity;
import com.example.smartmoney.infrastructure.persistence.mappers.NotificationMapper;
import com.example.smartmoney.infrastructure.persistence.repositories.NotificationJpaRepository;
import com.example.smartmoney.domain.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationJpaRepository jpaRepository;
    private final NotificationMapper mapper;

    @Override
    public Notification save(Notification notification) {
        NotificationEntity entity = mapper.toEntity(notification);
        NotificationEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    @Override
    public Optional<LocalDateTime> findMinCreatedAtInLatestNotifications(int limit) {
        return jpaRepository.findMinCreatedAtInLatestNotifications(limit);
    }

    @Override
    public void deleteOlderThan(LocalDateTime threshold) {
        jpaRepository.deleteByCreatedAtBefore(threshold);
    }
}

