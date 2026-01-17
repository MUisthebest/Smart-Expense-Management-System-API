package com.example.smartmoney.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
@Configuration
@ConfigurationProperties(prefix = "app.google.oauth2")
public class GoogleOAuthProperties {
    
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private Integer tokenValiditySeconds = 3600;
    
    @PostConstruct
    public void init() {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("Google OAuth clientId must be configured");
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("Google OAuth clientSecret must be configured");
        }
    }
    
    // Getters and Setters
    public String getClientId() {
        return clientId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    public String getClientSecret() {
        return clientSecret;
    }
    
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
    
    public String getRedirectUri() {
        return redirectUri;
    }
    
    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }
    
    public Integer getTokenValiditySeconds() {
        return tokenValiditySeconds;
    }
    
    public void setTokenValiditySeconds(Integer tokenValiditySeconds) {
        this.tokenValiditySeconds = tokenValiditySeconds;
    }
    
    @Override
    public String toString() {
        return "GoogleOAuthProperties{" +
                "clientId='" + (clientId != null ? "***" + clientId.substring(Math.max(0, clientId.length() - 4)) : "null") + '\'' +
                ", redirectUri='" + redirectUri + '\'' +
                ", tokenValiditySeconds=" + tokenValiditySeconds +
                '}';
    }
}