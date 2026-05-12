package com.example.smartmoney.infrastructure.persistence.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectHistoryEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "month")
    private Integer month;

    @Column(name = "year")
    private Integer year;

    @Column(name = "money_saved_before", precision = 19, scale = 2)
    private BigDecimal moneySavedBefore;

    @Column(name = "money_saved_after", precision = 19, scale = 2)
    private BigDecimal moneySavedAfter;

    @Column(name = "monthly_saving", precision = 19, scale = 2)
    private BigDecimal monthlySaving;

    @Column(name = "penalty", precision = 19, scale = 2)
    private BigDecimal penalty;

    @Column(name = "surplus_invested", precision = 19, scale = 2)
    private BigDecimal surplusInvested;

    @Column(name = "month_left_before")
    private Integer monthLeftBefore;

    @Column(name = "month_left_after")
    private Integer monthLeftAfter;

    @Column(name = "created_at")
    private Instant createdAt;
}
