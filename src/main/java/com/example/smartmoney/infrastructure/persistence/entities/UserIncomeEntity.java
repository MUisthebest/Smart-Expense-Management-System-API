package com.example.smartmoney.infrastructure.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_income")
@Getter
@Setter
public class UserIncomeEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "net_income")
    private Integer netIncome;

    @Column(name = "usable_income")
    private Integer usableIncome;

    @Column(name = "safe_spending")
    private Integer safeSpending;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "calculation_note", length = 255)
    private String calculationNote;

    @Column(name = "auto_invest_surplus")
    private Boolean autoInvestSurplus = Boolean.FALSE;

    @Column(name = "last_calculated_at")
    private Instant lastCalculatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
