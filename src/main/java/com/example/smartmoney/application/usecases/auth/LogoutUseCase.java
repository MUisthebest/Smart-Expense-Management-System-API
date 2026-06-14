package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.domain.exceptions.AuthenticationException;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class LogoutUseCase {

    private final UserRepository userRepository;

    public void execute(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationException("User not found"));
        user.clearRefreshToken();
        userRepository.save(user);
    }
}
