package com.example.smartmoney.domain.exceptions;

public class AuthenticationException extends DomainException {
    public AuthenticationException(String message) {
        super(message, "AUTHENTICATION_ERROR");
    }
    
    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}