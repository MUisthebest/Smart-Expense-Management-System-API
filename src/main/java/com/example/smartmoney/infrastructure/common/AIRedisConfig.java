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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;

import java.time.Duration;

/**
 * AI-specific Redis Configuration for AI/ML features
 * This config creates a separate Redis instance for AI-related caching and data storage
 */
@Configuration
public class AIRedisConfig {

    private static final Logger logger = LoggerFactory.getLogger(AIRedisConfig.class);

    @Value("${REDIS_HOST}")
    private String redisHost;

    @Value("${REDIS_PORT}")
    private int redisPort;

    @Value("${REDIS_USERNAME}")
    private String redisUsername;

    @Value("${REDIS_PASSWORD}")
    private String redisPassword;

    @Value("${REDIS_DATABASE:0}")
    private int redisDatabase;

    /**
     * AI Redis Connection Factory
     * Creates a separate connection factory for AI Redis instance
     */
    @Bean(name = "aiRedisConnectionFactory")
    public RedisConnectionFactory aiRedisConnectionFactory(
            io.lettuce.core.resource.ClientResources redisClientResources) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        config.setUsername(redisUsername);
        config.setPassword(redisPassword);
        config.setDatabase(redisDatabase);

        logger.warn(
                "BE AI REDIS host={} port={} db={} ssl={}",
                redisHost,
                redisPort,
                redisDatabase,
                false
        );

        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(Duration.ofSeconds(10))
                .keepAlive(true)
                .tcpNoDelay(true)
                .build();

        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(socketOptions)
                .autoReconnect(true)
                .build();

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .clientOptions(clientOptions)
                .clientResources(redisClientResources)
                .commandTimeout(Duration.ofSeconds(10))
                .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config, clientConfig);
        // Validate the shared connection on every checkout so a broken/stale
        // socket (idle timeout, NAT drop, provider restart) is detected and
        // transparently re-established instead of surfacing as an error.
        factory.setValidateConnection(true);
        return factory;
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
