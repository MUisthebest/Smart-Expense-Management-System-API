package com.example.smartmoney.infrastructure.persistence.repositories;


import com.example.smartmoney.infrastructure.persistence.entities.PushTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface JpaPushTokenRepository extends JpaRepository<PushTokenEntity, String> {

    Optional<PushTokenEntity> findByPushToken(String pushToken);

    List<PushTokenEntity> findByUserId(String userId);
}
