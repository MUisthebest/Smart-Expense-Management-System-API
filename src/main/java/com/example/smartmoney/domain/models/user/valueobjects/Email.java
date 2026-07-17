package com.example.smartmoney.domain.models.user.valueobjects;

import com.example.smartmoney.domain.exceptions.DomainException;

public class Email {
    private static final int MAX_TOTAL_LENGTH = 254;
    private static final int MAX_LOCAL_PART_LENGTH = 64;
    private static final int MAX_DOMAIN_PART_LENGTH = 190;

    private final String value;

    protected Email() {
        this.value = null;
    }

    public Email(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException("Email cannot be empty");
        }

        String normalizedValue = value.trim();

        if (normalizedValue.length() > MAX_TOTAL_LENGTH) {
            throw new DomainException("Email must not exceed 254 characters");
        }

        int atIndex = normalizedValue.indexOf('@');
        int lastAtIndex = normalizedValue.lastIndexOf('@');

        if (atIndex <= 0 || atIndex != lastAtIndex || atIndex == normalizedValue.length() - 1) {
            throw new DomainException("Invalid email format");
        }

        String localPart = normalizedValue.substring(0, atIndex);
        String domainPart = normalizedValue.substring(atIndex + 1);

        if (localPart.length() > MAX_LOCAL_PART_LENGTH) {
            throw new DomainException("Email local part must not exceed 64 characters");
        }
        if (domainPart.length() > MAX_DOMAIN_PART_LENGTH) {
            throw new DomainException("Email domain part must not exceed 190 characters");
        }
        if (!isValidEmail(normalizedValue)) {
            throw new DomainException("Invalid email format");
        }

        this.value = normalizedValue.toLowerCase();
    }

    private boolean isValidEmail(String email) {
        String emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$";
        return email.matches(emailRegex);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Email other = (Email) o;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}