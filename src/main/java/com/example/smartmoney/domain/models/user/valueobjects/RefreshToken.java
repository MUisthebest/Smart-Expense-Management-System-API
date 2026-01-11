package com.example.smartmoney.domain.models.user.valueobjects;

import java.time.LocalDateTime;

public class RefreshToken {
    private final String token;
    private final LocalDateTime expiresAt;

    protected RefreshToken() {
        this.token = null;
        this.expiresAt = null;
    }    
    
    public RefreshToken(String token, LocalDateTime expiresAt) {
        this.token = token;
        this.expiresAt = expiresAt;
    }
    
    public static RefreshToken create(String token, long expirationMinutes) {
        return new RefreshToken(token, LocalDateTime.now().plusMinutes(expirationMinutes));
    }
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
    
    public String getToken() {
        return token;
    }
    
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}