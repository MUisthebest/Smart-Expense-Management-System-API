package com.example.smartmoney.application.ports.notification;

import com.example.smartmoney.domain.models.notification.PushToken;

import java.util.List;
import java.util.Optional;

public interface PushTokenPort {

    void saveOrUpdate(PushToken model);

    Optional<PushToken> findByToken(String token);

    List<PushToken> findByUserId(String userId);
}