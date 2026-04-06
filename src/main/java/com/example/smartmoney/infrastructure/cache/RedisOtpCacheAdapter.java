package com.example.smartmoney.infrastructure.cache;

import com.example.smartmoney.application.ports.otp.OtpCachePort;
import com.example.smartmoney.domain.models.otp.OTP;
import com.example.smartmoney.domain.models.user.valueobjects.Email;
import com.example.smartmoney.infrastructure.security.OtpHasher;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisOtpCacheAdapter implements OtpCachePort {

    private final RedisTemplate<String, String> redisTemplate;

    public RedisOtpCacheAdapter(
            @Qualifier("coreStringRedisTemplate") RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    private static final String KEY_PREFIX = "otp:";

    @Override
    public void save(OTP otp) {

        String key = KEY_PREFIX + otp.getEmail().getValue();

        String hashedOtp = OtpHasher.hash(otp.getOtp());

        Duration ttl = Duration.between(
                java.time.LocalDateTime.now(),
                otp.getExpiredAt()
        );

        redisTemplate.opsForValue().set(key, hashedOtp, ttl);
    }

    @Override
    public boolean verify(Email email, String rawOtp) {

        String key = KEY_PREFIX + email.getValue();

        String storedHash = redisTemplate.opsForValue().get(key);

        if (storedHash == null) {
            return false;
        }

        String inputHash = OtpHasher.hash(rawOtp);

        return storedHash.equals(inputHash);
    }

    @Override
    public void deleteByEmail(Email email) {
        redisTemplate.delete(KEY_PREFIX + email.getValue());
    }
}