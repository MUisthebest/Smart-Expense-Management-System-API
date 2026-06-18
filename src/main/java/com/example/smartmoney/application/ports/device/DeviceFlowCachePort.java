package com.example.smartmoney.application.ports.device;

import java.util.Optional;

/**
 * Port for caching Device Flow state in Redis.
 *
 * Keys stored:
 *   device:{deviceCode}  → DeviceSession JSON
 *   usercode:{userCode}  → deviceCode (reverse lookup)
 */
public interface DeviceFlowCachePort {

    /** Persist a new device session (TTL = expiresIn seconds). */
    void saveSession(DeviceSession session);

    /** Look up a session by device_code. */
    Optional<DeviceSession> findByDeviceCode(String deviceCode);

    /** Look up device_code by user_code. */
    Optional<String> findDeviceCodeByUserCode(String userCode);

    /** Mark session as APPROVED and attach userId. */
    void approve(String deviceCode, String userId);

    /** Remove both keys after token is issued. */
    void delete(String deviceCode, String userCode);

    // ---- inner value object ----
    record DeviceSession(
            String deviceCode,
            String userCode,
            String clientId,
            String status,      // PENDING | APPROVED | EXPIRED
            String userId,      // null until approved
            int expiresIn
    ) {}
}