package com.example.smartmoney.domain.exceptions;

public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("INVALID_REFRESH_TOKEN");
    }

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}