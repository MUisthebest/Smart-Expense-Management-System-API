package com.example.smartmoney.domain.repositories;

import com.example.smartmoney.domain.models.notification.PushToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushTokenRepository {

    Optional<PushToken> findByPushToken(String pushToken);

    List<PushToken> findByUserId(UUID userId);

    void save(PushToken pushToken);
}