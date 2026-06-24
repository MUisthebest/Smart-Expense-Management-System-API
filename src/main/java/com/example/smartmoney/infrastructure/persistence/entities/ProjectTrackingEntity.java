package com.example.smartmoney.infrastructure.persistence.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_tracking",
        uniqueConstraints = @UniqueConstraint(name = "uk_project_tracking_project", columnNames = {"project_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectTrackingEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "money_saved", nullable = false, precision = 19, scale = 2)
    private BigDecimal moneySaved;

    @Column(name = "current_month", nullable = false)
    private Integer currentMonth;

    @Column(name = "monthly_saving", nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlySaving;

    @Column(name = "month_left", nullable = false)
    private Integer monthLeft;

    @Column(name = "money_owed", nullable = false, precision = 19, scale = 2)
    private BigDecimal moneyOwed;

    @Column(name = "frozen_months", nullable = false)
    @Builder.Default
    private Integer frozenMonths = 0;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
