package com.example.smartmoney.domain.models.project.valueobjects;

import java.util.Objects;
import java.util.UUID;

public final class ContributionId {
    private final UUID value;

    public ContributionId(UUID value) {
        if (value == null) throw new IllegalArgumentException("ContributionId is null");
        this.value = value;
    }

    public static ContributionId newId() {
        return new ContributionId(UUID.randomUUID());
    }

    public static ContributionId of(String raw) {
        return new ContributionId(UUID.fromString(raw));
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContributionId that)) return false;
        return value.equals(that.value);
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
