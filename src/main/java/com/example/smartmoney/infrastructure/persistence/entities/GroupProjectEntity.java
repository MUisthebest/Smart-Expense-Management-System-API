package com.example.smartmoney.infrastructure.persistence.entities;

import com.example.smartmoney.domain.models.group.enums.GroupProjectStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "group_projects",
        uniqueConstraints = @UniqueConstraint(name = "uk_group_project_group", columnNames = {"group_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupProjectEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "target_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "total_capacity", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalCapacity;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "total_months", nullable = false)
    private Integer totalMonths;

    @Column(name = "deadline", nullable = false)
    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private GroupProjectStatus status = GroupProjectStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
