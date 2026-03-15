package com.example.smartmoney.infrastructure.config;

//This file configs the redis cache
@Configuration

public class RedisConfig {
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LecttuceRedisConnectionFactory("localhost", 6379);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}