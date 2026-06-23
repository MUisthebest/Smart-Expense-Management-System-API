package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.requests.notification.SavePushTokenRequest;
import com.example.smartmoney.application.services.PushTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.example.smartmoney.infrastructure.security.JwtTokenProvider;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/push-tokens")
@RequiredArgsConstructor
public class PushTokenController {

    private final PushTokenService service;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping
    public void save(
        @RequestBody SavePushTokenRequest request,
        @RequestHeader("Authorization") String authHeader
    ) {
        UUID userId = extractUserIdFromToken(authHeader);
        service.saveToken(userId, request);
    }

        /**
     * Extract user ID from JWT token in Authorization header
     */
    private UUID extractUserIdFromToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return jwtTokenProvider.getUserIdFromToken(token);
        }
        throw new IllegalArgumentException("Invalid Authorization header");
    }
}