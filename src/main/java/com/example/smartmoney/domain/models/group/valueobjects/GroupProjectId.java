package com.example.smartmoney.domain.models.group.valueobjects;

import java.util.Objects;
import java.util.UUID;

public final class GroupProjectId {
    private final UUID value;

    public GroupProjectId(UUID value) {
        if (value == null) throw new IllegalArgumentException("GroupProjectId value is null");
        this.value = value;
    }

    public static GroupProjectId newId() { return new GroupProjectId(UUID.randomUUID()); }
    public static GroupProjectId of(String raw) { return new GroupProjectId(UUID.fromString(raw)); }

    public UUID value() { return value; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GroupProjectId g)) return false;
        return value.equals(g.value);
    }
    @Override public int hashCode() { return Objects.hash(value); }
    @Override public String toString() { return value.toString(); }
}
