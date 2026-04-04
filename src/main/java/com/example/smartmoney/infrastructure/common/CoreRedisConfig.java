package com.example.smartmoney.infrastructure.common;

import com.example.smartmoney.domain.models.otp.OTP;
import com.example.smartmoney.domain.models.notification.Notification;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Core Redis Configuration for main application use cases
 * This config handles: notifications, OTP, and generic object storage
 */
@Configuration
public class CoreRedisConfig {

    /**
     * Primary generic RedisTemplate for main Redis instance (default)
     * Used for: notifications, sessions, and general caching
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> coreRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        Jackson2JsonRedisSerializer<Object> serializer = 
            new Jackson2JsonRedisSerializer<>(mapper, Object.class);
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Alias for coreRedisTemplate with name 'redisTemplate' for backward compatibility
     * This uses the same bean as coreRedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            @Qualifier("coreRedisTemplate") RedisTemplate<String, Object> coreRedisTemplate) {
        return coreRedisTemplate;
    }

    /**
     * OTP-specific RedisTemplate for OTP storage
     */
    @Bean
    public RedisTemplate<String, OTP> otpRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, OTP> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        Jackson2JsonRedisSerializer<OTP> serializer = 
            new Jackson2JsonRedisSerializer<>(mapper, OTP.class);
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Notification-specific RedisTemplate for notification storage
     */
    @Bean
    public RedisTemplate<String, Notification> notificationRedisTemplate(
        RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Notification> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        Jackson2JsonRedisSerializer<Notification> serializer =
                new Jackson2JsonRedisSerializer<>(mapper, Notification.class);

        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }
}