package com.example.smartmoney.infrastructure.notification.adapters;

import com.example.smartmoney.application.ports.notification.PushTokenPort;
import com.example.smartmoney.domain.models.notification.PushToken;
import com.example.smartmoney.infrastructure.persistence.entities.PushTokenEntity;
import com.example.smartmoney.infrastructure.persistence.mappers.PushTokenMapper;
import com.example.smartmoney.infrastructure.persistence.repositories.JpaPushTokenRepository;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PushTokenAdapter implements PushTokenPort {

    private final JpaPushTokenRepository jpaRepo;

    @Override
    public void saveOrUpdate(PushToken model) {

        var existing = jpaRepo.findByPushToken(model.getPushToken());

        if (existing.isPresent()) {
            PushTokenEntity entity = existing.get();
            entity.setUserId(model.getUserId());
            entity.setPlatform(model.getPlatform());
            entity.setUpdatedAt(LocalDateTime.now());
            jpaRepo.save(entity);
            return;
        }

        jpaRepo.save(PushTokenMapper.toEntity(model));
    }

    @Override
    public Optional<PushToken> findByToken(String token) {
        return jpaRepo.findByPushToken(token)
                .map(PushTokenMapper::toDomain);
    }

    @Override
    public List<PushToken> findByUserId(String userId) {
        return jpaRepo.findByUserId(UUID.fromString(userId))
                .stream()
                .map(PushTokenMapper::toDomain)
                .collect(Collectors.toList());
    }
}