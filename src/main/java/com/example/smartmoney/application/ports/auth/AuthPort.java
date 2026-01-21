package com.example.smartmoney.application.ports.auth;

import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.valueobjects.Email;

public interface AuthPort {
    String generateAccessToken(User user);
    String generateRefreshToken(User user);
    boolean validatePassword(String rawPassword, String encodedPassword);
    String encodePassword(String rawPassword);
    User validateRefreshToken(String refreshToken);    
    default User getUserByEmail(Email email) {
        throw new UnsupportedOperationException("Method not implemented");
    }

}