package com.example.smartmoney.domain.models.user.valueobjects;
import com.example.smartmoney.domain.exceptions.DomainException;
import java.util.Objects;

public class Phone {
    private final String value;

    protected Phone() {
        this.value = null;
    }
    
    public Phone(String value) {
        if (value == null || value.isBlank()) {
            this.value = null;
            return;
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
        if (value == null) {
            return null;
        }
        return value;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Phone)) return false;
        Phone other = (Phone) o;
        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}