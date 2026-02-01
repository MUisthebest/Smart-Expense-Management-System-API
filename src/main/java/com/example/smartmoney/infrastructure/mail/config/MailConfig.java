package com.example.smartmoney.infrastructure.mail.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Properties;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MailConfig {

    private final MailProperties mailProperties;

    @Bean
    public JavaMailSender javaMailSender() {
        try {
            log.info("Initializing JavaMailSender...");
            mailProperties.logConfiguration();
            
            JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
            
            // Set basic properties
            mailSender.setHost(mailProperties.getHost());
            mailSender.setPort(mailProperties.getPort());
            mailSender.setUsername(mailProperties.getUsername());
            mailSender.setPassword(mailProperties.getPassword());
            mailSender.setProtocol(mailProperties.getProtocol());
            mailSender.setDefaultEncoding(mailProperties.getDefaultEncoding());
            
            // Set JavaMail properties
            Properties javaMailProperties = new Properties();
            javaMailProperties.put("mail.smtp.auth", String.valueOf(mailProperties.isSmtpAuthEnabled()));
            javaMailProperties.put("mail.smtp.starttls.enable", String.valueOf(mailProperties.isStartTlsEnabled()));
            javaMailProperties.put("mail.smtp.starttls.required", String.valueOf(mailProperties.isStartTlsRequired()));
            
            // Optional: Add debug mode for development
            javaMailProperties.put("mail.debug", "true");
            
            mailSender.setJavaMailProperties(javaMailProperties);
            
            log.info("JavaMailSender initialized successfully");
            return mailSender;
            
        } catch (Exception e) {
            log.error("Failed to initialize JavaMailSender", e);
            throw new RuntimeException("Could not initialize JavaMailSender", e);
        }
    }
}