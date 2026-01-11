package com.example.smartmoney.domain.exceptions;

public class AuthorizationException extends DomainException {
    public AuthorizationException(String message) {
        super(message, "AUTHORIZATION_ERROR");
    }
}