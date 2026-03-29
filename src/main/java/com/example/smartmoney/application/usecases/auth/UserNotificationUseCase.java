package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserNotificationUseCase {

    private final UserRepository userRepository;

    /**
     * Bật notification
     */
    @Transactional
    public void enable(UUID userId) {
        User user = getUser(userId);
        user.enableNotification();
        userRepository.save(user);
    }

    /**
     * Tắt notification
     */
    @Transactional
    public void disable(UUID userId) {
        User user = getUser(userId);
        user.disableNotification();
        userRepository.save(user);
    }

    /**
     * Lấy trạng thái notification
     */
    @Transactional(readOnly = true)
    public boolean getStatus(UUID userId) {
        User user = getUser(userId);
        return user.isNotificationEnabled();
    }

    /**
     * Toggle (optional - tiện cho UI)
     */
    @Transactional
    public boolean toggle(UUID userId) {
        User user = getUser(userId);

        if (user.isNotificationEnabled()) {
            user.disableNotification();
        } else {
            user.enableNotification();
        }

        userRepository.save(user);
        return user.isNotificationEnabled();
    }

    // helper
    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}