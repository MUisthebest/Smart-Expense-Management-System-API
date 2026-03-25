package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.domain.models.notification.Notification;
import com.example.smartmoney.domain.repositories.NotificationRepository;
import com.example.smartmoney.infrastructure.persistence.entities.NotificationEntity;
import com.example.smartmoney.infrastructure.persistence.mappers.NotificationMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {

    private final JpaNotificationRepository jpaRepository;
    private final NotificationMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Notification save(Notification notification) {
        NotificationEntity entity = mapper.toEntity(notification);
        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId) {
        // ❗ tránh findAll + filter (rất tệ performance)
        return jpaRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteExceedingByUserId(UUID userId, int max) {
        entityManager.createNativeQuery("""
            DELETE FROM notifications
            WHERE id IN (
                SELECT id FROM notifications
                WHERE user_id = :userId
                ORDER BY created_at DESC
                OFFSET :max
            )
        """)
        .setParameter("userId", userId)
        .setParameter("max", max)
        .executeUpdate();
    }
}