package com.example.smartmoney.infrastructure.persistence.entities;

import com.example.smartmoney.domain.models.userprofile.enums.FamilyStatus;
import com.example.smartmoney.domain.models.userprofile.enums.HealthNeed;
import com.example.smartmoney.domain.models.userprofile.enums.IncomeLevel;
import com.example.smartmoney.domain.models.userprofile.enums.LivingStatus;
import com.example.smartmoney.domain.models.userprofile.enums.SpendingStyle;
import com.example.smartmoney.domain.models.userprofile.enums.StudyIntensity;
import com.example.smartmoney.domain.models.userprofile.enums.TransportMode;
import com.example.smartmoney.domain.models.userprofile.enums.UserRole;
import com.example.smartmoney.domain.models.userprofile.enums.WorkStyle;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_financial_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserFinancialProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "living_status", nullable = false)
    private LivingStatus livingStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "income_level", nullable = false)
    private IncomeLevel incomeLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_mode", nullable = false)
    private TransportMode transportMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "spending_style", nullable = false)
    private SpendingStyle spendingStyle;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_style", nullable = false)
    private WorkStyle workStyle;

    @Enumerated(EnumType.STRING)
    @Column(name = "family_status", nullable = false)
    private FamilyStatus familyStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "study_intensity", nullable = false)
    private StudyIntensity studyIntensity;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_need", nullable = false)
    private HealthNeed healthNeed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}