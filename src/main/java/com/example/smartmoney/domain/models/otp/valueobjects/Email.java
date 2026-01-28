package com.example.smartmoney.domain.models.otp.valueobjects;
import com.example.smartmoney.domain.exceptions.DomainException;

public class Email {
    private final String value;

     protected Email() {
        this.value = null;
    }   
    
    public Email(String value) {
        if (value == null || value.isEmpty()) {
            throw new DomainException("Email cannot be empty");
        }
        if (value.length() > 30) {
            throw new DomainException("Email must not exceed 30 characters");
        }
        if (!isValidEmail(value)) {
            throw new DomainException("Invalid email format");
        }
        this.value = value.toLowerCase();
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