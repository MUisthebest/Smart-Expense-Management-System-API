package com.example.smartmoney.domain.exceptions;

import java.util.Map;

public class ValidationException extends DomainException {
    private final Map<String, String> errors;
    
    public ValidationException(String message, Map<String, String> errors) {
        super(message, "VALIDATION_ERROR");
        this.errors = errors;
    }
    
    public Map<String, String> getErrors() {
        return errors;
    }
}