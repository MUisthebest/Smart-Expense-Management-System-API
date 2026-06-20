package com.example.smartmoney.application.services;

import com.example.smartmoney.application.dtos.requests.notification.SavePushTokenRequest;
import com.example.smartmoney.application.ports.notification.PushTokenPort;
import com.example.smartmoney.domain.models.notification.PushToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PushTokenService {

    private final PushTokenPort repository;

    public void saveToken(UUID userId, SavePushTokenRequest request) {

        PushToken model = PushToken.builder()
                .userId(userId)
                .pushToken(request.getPushToken())
                .platform(request.getPlatform())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        repository.saveOrUpdate(model);
    }
}