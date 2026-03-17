package com.example.smartmoney.infrastructure.config.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.*;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class RedisConfig {

    // ===== CONNECTION FACTORY =====
    @Bean
    public RedisConnectionFactory redis6379ConnectionFactory() {
        return new LettuceConnectionFactory("localhost", 6379);
    }

    @Bean
    public RedisConnectionFactory redis6380ConnectionFactory() {
        return new LettuceConnectionFactory("localhost", 6380);
    }

    // ===== TEMPLATE 6379 =====
    @Bean(name = "redis6379Template")
    public RedisTemplate<String, Object> redis6379Template(
            @Qualifier("redis6379ConnectionFactory") RedisConnectionFactory cf
    ) {
        return createTemplate(cf);
    }

    // ===== TEMPLATE 6380 =====
    @Bean(name = "redis6380Template")
    public RedisTemplate<String, Object> redis6380Template(
            @Qualifier("redis6380ConnectionFactory") RedisConnectionFactory cf
    ) {
        return createTemplate(cf);
    }

    // ===== COMMON TEMPLATE CONFIG =====
    private RedisTemplate<String, Object> createTemplate(RedisConnectionFactory cf) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(cf);

        // KEY
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // VALUE
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }

    // ===== ADAPTER =====
    @Bean
    public RedisJobCacheAdapter redisJobCacheAdapter(
            @Qualifier("redis6379Template") RedisTemplate<String, Object> template
    ) {
        return new RedisJobCacheAdapter(template);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            @Qualifier("redis6379Template") RedisTemplate<String, Object> template
    ) {
        return template;
    }
}