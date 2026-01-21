package com.example.smartmoney.infrastructure.auth.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConfigurationProperties(prefix = "app.supabase")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupabaseJwtProperties {
    
    private String projectUrl;      // e.g., https://xxxxxxxxxxxx.supabase.co
    private String anonKey;         // Anon/Public Key - REQUIRED for frontend auth
    private String serviceRoleKey;  // Service Role Key (optional, only for admin operations)
    private String jwtJwk;          // ES256 Public Key in JWK format - for token validation
    private Boolean enabled = true;
    
    /**
     * Get JWT secret for token validation.
     * Uses anon key by default (for frontend authentication).
     * Falls back to service role key if anon key is not available.
     */
    public String getJwtSecret() {
        if (anonKey != null && !anonKey.isEmpty()) {
            return anonKey;
        }
        if (serviceRoleKey != null && !serviceRoleKey.isEmpty()) {
            return serviceRoleKey;
        }
        throw new IllegalStateException("Supabase anonKey or serviceRoleKey must be configured");
    }
    
    public void validateConfig() {
        if (!enabled) return;
        
        if (projectUrl == null || projectUrl.isEmpty()) {
            log.warn("⚠️ Supabase projectUrl is not configured");
        }
        
        // At least one key must be configured
        if ((anonKey == null || anonKey.isEmpty()) && 
            (serviceRoleKey == null || serviceRoleKey.isEmpty())) {
            log.error("❌ Either Supabase anonKey or serviceRoleKey must be configured");
            throw new IllegalStateException("Supabase credentials not configured");
        }
        
        // JWK must be configured for ES256 token validation
        if (jwtJwk == null || jwtJwk.isEmpty()) {
            log.error("❌ Supabase JWT_JWK must be configured for ES256 token validation");
            throw new IllegalStateException("Supabase JWT_JWK not configured");
        }
        
        if (anonKey != null && !anonKey.isEmpty()) {
            log.info("✅ Supabase anonKey is configured for frontend authentication");
        }
        if (serviceRoleKey != null && !serviceRoleKey.isEmpty()) {
            log.info("✅ Supabase serviceRoleKey is configured for admin operations");
        }
        if (jwtJwk != null && !jwtJwk.isEmpty()) {
            log.info("✅ Supabase JWT_JWK is configured for ES256 token validation");
        }
    }
}
