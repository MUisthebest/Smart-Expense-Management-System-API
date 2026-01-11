package com.example.smartmoney.domain.models.user.valueobjects;

import com.example.smartmoney.domain.exceptions.DomainException;
import java.util.Objects;

public final class Password {

    private final String hash;

    protected Password() {
      this.hash = null;  
    }

    public Password(String hash) {
        if (hash == null || hash.isBlank()) {
            throw new DomainException("Password hash cannot be empty");
        }
        this.hash = hash;
    }

    /**
     * Factory method
     * Nhận password ĐÃ ĐƯỢC ENCODE từ Application layer
     */
    public static Password fromHash(String hash) {
        return new Password(hash);
    }

    public String getHash() {
        return hash;
    }

    // Value Object equality
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Password)) return false;
        Password password = (Password) o;
        return hash.equals(password.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash);
    }
}
