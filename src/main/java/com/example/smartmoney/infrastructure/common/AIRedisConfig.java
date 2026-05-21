package com.example.smartmoney.infrastructure.common;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * AI-specific Redis Configuration for AI/ML features
 * This config creates a separate Redis instance for AI-related caching and data storage
 */
@Configuration
public class AIRedisConfig {

    @Value("${REDIS_HOST}")
    private String redisHost;

    @Value("${REDIS_PORT}")
    private int redisPort;

    @Value("${REDIS_USERNAME}")
    private String redisUsername;

    @Value("${REDIS_PASSWORD}")
    private String redisPassword;

    /**
     * AI Redis Connection Factory
     * Creates a separate connection factory for AI Redis instance
     */
    @Bean(name = "aiRedisConnectionFactory")
    public RedisConnectionFactory aiRedisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        config.setUsername(redisUsername);
        config.setPassword(redisPassword);

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder().build();
        return new LettuceConnectionFactory(config, clientConfig);
    }

    /**
     * AI-specific RedisTemplate
     * Dedicated template for AI/ML features with separate connection
     * Qualifier: "aiRedisTemplate"
     */
    @Bean(name = "aiRedisTemplate")
    public RedisTemplate<String, Object> aiRedisTemplate(
            @Qualifier("aiRedisConnectionFactory") RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();

        Jackson2JsonRedisSerializer<Object> valueSerializer =
                new Jackson2JsonRedisSerializer<>(Object.class);

        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules(); // hỗ trợ LocalDateTime nếu có
        valueSerializer.setObjectMapper(mapper);

        template.setKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);

        template.setHashKeySerializer(keySerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean(name = "aiStringRedisTemplate")
    public StringRedisTemplate aiStringRedisTemplate(
            @Qualifier("aiRedisConnectionFactory") RedisConnectionFactory connectionFactory
    ) {
        return new StringRedisTemplate(connectionFactory);
    }
}