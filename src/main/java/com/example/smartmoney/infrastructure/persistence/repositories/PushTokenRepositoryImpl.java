package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.domain.models.notification.PushToken;
import com.example.smartmoney.domain.repositories.PushTokenRepository;
import com.example.smartmoney.infrastructure.persistence.entities.PushTokenEntity;
import com.example.smartmoney.infrastructure.persistence.mappers.PushTokenMapper;
import com.example.smartmoney.infrastructure.persistence.repositories.JpaPushTokenRepository;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PushTokenRepositoryImpl implements PushTokenRepository {

    private final JpaPushTokenRepository jpaPushTokenRepository;

    @Override
    public Optional<PushToken> findByPushToken(String pushToken) {
        Optional<PushTokenEntity> entityOpt = jpaPushTokenRepository.findByPushToken(pushToken);
        return entityOpt.map(PushTokenMapper::toDomain);
    }

    @Override
    public List<PushToken> findByUserId(UUID userId) {
        List<PushTokenEntity> entities = jpaPushTokenRepository.findByUserId(userId);
        return entities.stream()
                .map(PushTokenMapper::toDomain)
                .toList();
    }

    @Override
    public void save(PushToken pushToken) {
        PushTokenEntity entity = PushTokenMapper.toEntity(pushToken);
        jpaPushTokenRepository.save(entity);
    }
}