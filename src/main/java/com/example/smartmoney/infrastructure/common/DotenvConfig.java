package com.example.smartmoney.infrastructure.common;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class DotenvConfig {

    static {
        // Load .env file at class initialization time to ensure environment variables are available early
        try {
            Dotenv dotenv = Dotenv.configure()
                    .directory(".")
                    .filename(".env")
                    .load();
            
            // Load all environment variables from .env file into system properties
            dotenv.entries().forEach(entry -> {
                System.setProperty(entry.getKey(), entry.getValue());
            });
            
            // Log specific Facebook config for debugging
            String fbSecret = System.getProperty("APP_FACEBOOK_APP_SECRET");
            String fbId = System.getProperty("APP_FACEBOOK_APP_ID");
            if (fbSecret != null && !fbSecret.isEmpty()) {
                System.out.println("APP_FACEBOOK_APP_SECRET loaded from .env: " + fbSecret.substring(0, Math.min(10, fbSecret.length())) + "***");
            }
            if (fbId != null && !fbId.isEmpty()) {
                System.out.println("APP_FACEBOOK_APP_ID loaded from .env: " + fbId);
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not load .env file: " + e.getMessage());
        }
    }

    @PostConstruct
    public void logEnv() {
        log.info("Environment variables loaded successfully");
    }
}
