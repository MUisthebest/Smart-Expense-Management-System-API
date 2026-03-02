package com.example.smartmoney.domain.models.project.valueobjects;

import java.util.Objects;
import java.util.UUID;

public final class UserId {
    private final UUID value;

    public UserId(UUID value) {
        if (value == null) throw new IllegalArgumentException("UserId is null");
        this.value = value;
    }

    public static UserId of(String raw) {
        return new UserId(UUID.fromString(raw));
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserId userId)) return false;
        return value.equals(userId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
