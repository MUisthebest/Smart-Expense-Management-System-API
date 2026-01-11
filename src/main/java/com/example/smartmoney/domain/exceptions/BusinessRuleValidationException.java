package com.example.smartmoney.domain.exceptions;

public class BusinessRuleValidationException extends DomainException {
    public BusinessRuleValidationException(String message) {
        super(message, "BUSINESS_RULE_VIOLATION");
    }
    
    public BusinessRuleValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}