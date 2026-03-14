package com.example.smartmoney.infrastructure.common;

import com.example.smartmoney.domain.models.otp.OTP;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, OTP> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, OTP> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Serializer cho key
        template.setKeySerializer(new StringRedisSerializer());

        // Serializer cho value (dạng JSON)
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule()); // hỗ trợ kiểu thời gian Java 8
        Jackson2JsonRedisSerializer<OTP> serializer = 
            new Jackson2JsonRedisSerializer<>(mapper, OTP.class);
        
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }
}