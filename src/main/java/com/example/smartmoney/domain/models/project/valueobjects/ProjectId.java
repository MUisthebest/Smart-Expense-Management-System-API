package com.example.smartmoney.domain.models.project.valueobjects;

import java.util.Objects;
import java.util.UUID;

public final class ProjectId {
    private final UUID value;

    public ProjectId(UUID value) {
        if (value == null) throw new IllegalArgumentException("Project is null");
        this.value = value;
    }

    public static ProjectId newId() {
        return new ProjectId(UUID.randomUUID());
    }

    public static ProjectId of(String raw) {
        return new ProjectId(UUID.fromString(raw));
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProjectId projectId)) return false;
        return value.equals(projectId.value);
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
