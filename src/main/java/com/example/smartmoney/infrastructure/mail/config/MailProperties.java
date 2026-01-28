package com.example.smartmoney.infrastructure.mail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {

    // Basic mail properties
    private String host;
    private Integer port;
    private String username;
    private String password;
    private String protocol = "smtp";
    private String defaultEncoding = "UTF-8";
    
    // SMTP Properties
    private Properties properties = new Properties();
    
    // Nested Properties class
    @Getter
    @Setter
    public static class Properties {
        private Mail mail = new Mail();
    }
    
    @Getter
    @Setter
    public static class Mail {
        private Smtp smtp = new Smtp();
    }
    
    @Getter
    @Setter
    public static class Smtp {
        private Boolean auth;
        private Starttls starttls = new Starttls();
        
        @Getter
        @Setter
        public static class Starttls {
            private Boolean enable;
            private Boolean required;
        }
    }
    
    // Helper methods
    public boolean isSmtpAuthEnabled() {
        return properties.getMail().getSmtp().getAuth() != null 
            && properties.getMail().getSmtp().getAuth();
    }
    
    public boolean isStartTlsEnabled() {
        return properties.getMail().getSmtp().getStarttls().getEnable() != null 
            && properties.getMail().getSmtp().getStarttls().getEnable();
    }
    
    public boolean isStartTlsRequired() {
        return properties.getMail().getSmtp().getStarttls().getRequired() != null 
            && properties.getMail().getSmtp().getStarttls().getRequired();
    }
    
    // Debug method
    public void logConfiguration() {
        log.info("Mail Configuration:");
        log.info("  Host: {}", host);
        log.info("  Port: {}", port);
        log.info("  Username: {}", username);
        log.info("  Protocol: {}", protocol);
        log.info("  SMTP Auth: {}", isSmtpAuthEnabled());
        log.info("  STARTTLS Enabled: {}", isStartTlsEnabled());
        log.info("  STARTTLS Required: {}", isStartTlsRequired());
    }
}