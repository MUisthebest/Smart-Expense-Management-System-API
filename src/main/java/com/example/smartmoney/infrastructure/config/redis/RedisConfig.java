package com.example.smartmoney.infrastructure.config.redis;

import org.springframework.context.annotation.Configuration;

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
