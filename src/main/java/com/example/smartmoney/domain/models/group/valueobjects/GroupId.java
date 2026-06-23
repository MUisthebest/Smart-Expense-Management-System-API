package com.example.smartmoney.domain.models.group.valueobjects;

import java.util.Objects;
import java.util.UUID;

public final class GroupId {
    private final UUID value;

    public GroupId(UUID value) {
        if (value == null) throw new IllegalArgumentException("GroupId value is null");
        this.value = value;
    }

    public static GroupId newId() { return new GroupId(UUID.randomUUID()); }
    public static GroupId of(String raw) { return new GroupId(UUID.fromString(raw)); }

    public UUID value() { return value; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GroupId g)) return false;
        return value.equals(g.value);
    }
    @Override public int hashCode() { return Objects.hash(value); }
    @Override public String toString() { return value.toString(); }
}
