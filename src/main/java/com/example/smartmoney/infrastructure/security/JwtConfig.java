package com.example.smartmoney.infrastructure.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.jwt")
public class JwtConfig {
    
    private String secret;
    private long accessTokenExpiration;
    private long refreshTokenExpiration;
    private String issuer = "smart-money";
    private String audience = "smart-money-client";
    
    // Token prefix for Authorization header
    private String tokenPrefix = "Bearer ";
    
    // Header name
    private String headerName = "Authorization";
    
    // Refresh token cookie config
    private String refreshTokenCookieName = "refresh_token";
    private int refreshTokenCookieMaxAge = 604800; // 7 days in seconds
    private boolean refreshTokenCookieHttpOnly = true;
    private boolean refreshTokenCookieSecure = true;
    private String refreshTokenCookiePath = "/api/v1/auth/refresh-token";
}