# Multi-Redis Configuration Guide

## Overview
This project now supports **two separate Redis instances**:
1. **Core Redis** - Main application Redis for notifications, OTP, and general caching
2. **AI Redis** - Dedicated Redis for AI/ML features

## Architecture

### Core Redis Configuration (`CoreRedisConfig`)
- **Connection Factory**: Uses default Spring Data Redis auto-configuration (`spring.data.redis.url`)
- **RedisTemplate Beans**:
  - `redisTemplate` (primary) - Generic Object storage
  - `coreRedisTemplate` - Same as above (alternative name)
  - `otpRedisTemplate` - OTP-specific template
  - `notificationRedisTemplate` - Notification-specific template

### AI Redis Configuration (`AIRedisConfig`)
- **Connection Factory**: `aiRedisConnectionFactory` - Uses custom environment variables
  - `REDIS_HOST`
  - `REDIS_PORT`
  - `REDIS_USERNAME`
  - `REDIS_PASSWORD`
- **RedisTemplate Bean**:
  - `aiRedisTemplate` - AI/ML features template

## Environment Variables

### Core Redis (from application.yml)
```yaml
spring:
  data:
    redis:
      url: ${REDIS_URI:redis://localhost:6379}
      timeout: 10000ms
```

### AI Redis (from .env or system variables)
```
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_USERNAME=default
REDIS_PASSWORD=yourpassword
```

## Usage Examples

### Injecting Core RedisTemplate (Primary)
```java
@Service
public class NotificationService {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate; // Uses primary coreRedisTemplate
    
    // or explicitly use coreRedisTemplate
    @Autowired
    @Qualifier("coreRedisTemplate")
    private RedisTemplate<String, Object> coreRedis;
}
```

### Injecting OTP RedisTemplate
```java
@Service
public class OTPService {
    
    @Autowired
    private RedisTemplate<String, OTP> otpRedisTemplate;
    
    public void storeOTP(String key, OTP otp) {
        otpRedisTemplate.opsForValue().set(key, otp);
    }
}
```

### Injecting AI RedisTemplate
```java
@Service
public class AIService {
    
    @Autowired
    @Qualifier("aiRedisTemplate")
    private RedisTemplate<String, Object> aiRedis;
    
    public void storeAIResult(String key, Object result) {
        aiRedis.opsForValue().set(key, result);
    }
}
```

### Injecting Notification RedisTemplate
```java
@Service
public class EmailService {
    
    @Autowired
    private RedisTemplate<String, Notification> notificationRedisTemplate;
    
    public void storeNotification(String key, Notification notification) {
        notificationRedisTemplate.opsForValue().set(key, notification);
    }
}
```

## Key Points

### Problem Solved
- **BeanDefinitionOverrideException** is fixed by:
  - Using unique bean names (`aiRedisTemplate` instead of `redisTemplate`)
  - Making Core Redis templates `@Primary` for default injection
  - Using `@Qualifier` annotations to explicitly select which Redis instance to use

### Bean Naming Convention
| Bean Name | Usage | Primary | Qualifier Name |
|---|---|---|---|
| `redisTemplate` | Core, Main application | ✅ Yes | N/A |
| `coreRedisTemplate` | Core, Alternative | ✅ Yes | `coreRedisTemplate` |
| `otpRedisTemplate` | OTP storage | ❌ No | `otpRedisTemplate` |
| `notificationRedisTemplate` | Notifications | ❌ No | `notificationRedisTemplate` |
| `aiRedisTemplate` | AI/ML features | ❌ No | `aiRedisTemplate` |

### Connection Factories
| Factory Name | Usage | Configuration Source |
|---|---|---|
| (default/implicit) | Core Redis | `spring.data.redis.url` from application.yml |
| `aiRedisConnectionFactory` | AI Redis | Env vars: `REDIS_HOST`, `REDIS_PORT`, `REDIS_USERNAME`, `REDIS_PASSWORD` |

## Troubleshooting

### Issue: "No qualifying bean of type 'RedisTemplate'"
**Solution**: Add `@Qualifier("aiRedisTemplate")` annotation when injecting AI Redis

### Issue: "BeanDefinitionOverrideException"
**Solution**: This should be resolved. If it recurs:
1. Check for duplicate bean definitions in other config files
2. Verify bean names are unique across all @Configuration classes
3. Use `@Primary` only for default beans

### Issue: AI Redis connection fails
**Solution**: Verify environment variables are set correctly:
```bash
echo $REDIS_HOST
echo $REDIS_PORT
echo $REDIS_USERNAME
echo $REDIS_PASSWORD
```

## Migration Guide (if upgrading from single Redis)

### Before (Old Code)
```java
@Autowired
private RedisTemplate<String, Object> redisTemplate;
```

### After (New Code - No Change Needed!)
```java
@Autowired
private RedisTemplate<String, Object> redisTemplate; // Still works! Uses @Primary core Redis
```

### For AI Features
```java
@Autowired
@Qualifier("aiRedisTemplate")
private RedisTemplate<String, Object> aiRedisTemplate; // Use this for AI Redis
```

## Testing

To verify the setup works:
```bash
mvn clean install
mvn spring-boot:run
```

Check logs for:
```
INFO ... CoreRedisConfig : Initializing Core Redis Configuration
INFO ... AIRedisConfig : Initializing AI Redis Configuration
```

## Future Improvements

- [ ] Add Redis connection pooling configuration
- [ ] Add Redis cluster support
- [ ] Add Redis sentinel support for high availability
- [ ] Add Redis health checks
- [ ] Add Redis metrics/monitoring
