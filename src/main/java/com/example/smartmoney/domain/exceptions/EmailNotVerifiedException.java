package com.example.smartmoney.domain.exceptions;

public class EmailNotVerifiedException extends DomainException {

    public EmailNotVerifiedException() {
        super("Email is not verified");
    }

    public EmailNotVerifiedException(String message) {
        super(message);
    }
}