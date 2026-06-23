package com.example.smartmoney.infrastructure.common;

import com.example.smartmoney.domain.models.otp.OTP;
import com.example.smartmoney.domain.models.notification.Notification;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
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
    @Value("${spring.data.redis.url}")
    private String redisUri; // Lấy từ application.yml

    /**
     * Core Redis Connection Factory
     */
    @Bean(name = "coreRedisConnectionFactory")
    public RedisConnectionFactory coreRedisConnectionFactory() {
        URI uri = URI.create(redisUri);

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(uri.getHost());
        config.setPort(uri.getPort());

        if (uri.getUserInfo() != null) {
            String[] parts = uri.getUserInfo().split(":", 2);
            config.setUsername(parts[0]);
            config.setPassword(parts[1]);
        }
        

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder().build();


        return new LettuceConnectionFactory(config, clientConfig);
    }

    @Bean
    @Primary
    public RedisTemplate<String, Object> coreRedisTemplate(
           @Qualifier("coreRedisConnectionFactory") RedisConnectionFactory connectionFactory) {
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
           @Qualifier("coreRedisConnectionFactory") RedisConnectionFactory connectionFactory) {
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
        @Qualifier("coreRedisConnectionFactory") RedisConnectionFactory connectionFactory) {
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

    @Bean
    @Qualifier("coreStringRedisTemplate")
    public RedisTemplate<String, String> stringRedisTemplate(
            @Qualifier("coreRedisConnectionFactory") RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}