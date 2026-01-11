package com.example.smartmoney.domain.services;

import com.example.smartmoney.domain.models.user.User;

import org.springframework.stereotype.Service;

import com.example.smartmoney.domain.exceptions.AuthenticationException;

@Service
public class AuthDomainService {

    public interface PasswordEncoder {
        boolean matches(String rawPassword, String encodedPassword);
        String encode(String rawPassword);
    }

    public void authenticate(User user, String rawPassword, PasswordEncoder encoder) {
        if (!encoder.matches(rawPassword, user.getPassword().getHash())) {
            throw new AuthenticationException("Invalid email or password");
        }

        if (!user.isActive()) {
            throw new AuthenticationException("Account is deactivated");
        }
    }

    public void changePassword(
            User user,
            String oldPassword,
            String newPassword,
            PasswordEncoder encoder
    ) {
        if (!encoder.matches(oldPassword, user.getPassword().getHash())) {
            throw new AuthenticationException("Old password incorrect");
        }

        user.changePassword(encoder.encode(newPassword));
        user.clearRefreshToken();
    }
}
