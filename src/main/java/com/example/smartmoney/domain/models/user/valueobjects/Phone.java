package com.example.smartmoney.domain.models.user.valueobjects;
import com.example.smartmoney.domain.exceptions.DomainException;

public class Phone {
    private final String value;

    protected Phone() {
        this.value = null;
    }
    
    public Phone(String value) {
        if (value == null || value.isEmpty()) {
            throw new DomainException("Phone cannot be empty");
        }
        if (value.length() > 20) {
            throw new DomainException("Phone must not exceed 20 digits");
        }
        if (!value.matches("\\d+")) {
            throw new DomainException("Phone must contain only digits");
        }
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Phone other = (Phone) o;
        return value.equals(other.value);
    }
    
    @Override
    public int hashCode() {
        return value.hashCode();
    }
}