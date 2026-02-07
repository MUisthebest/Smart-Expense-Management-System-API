package com.example.smartmoney.infrastructure.persistence.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "project_contributions",
        indexes = {
            @Index(name = "idx_contrib_project", columnList = "project_id"),
            @Index(name = "idx_contrib_user", columnList = "user_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectContributionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "contribution_date", nullable = false)
    private LocalDate contributionDate;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
