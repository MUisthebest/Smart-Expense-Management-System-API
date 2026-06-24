package com.example.smartmoney.domain.models.userprofile;

import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.userprofile.enums.FamilyStatus;
import com.example.smartmoney.domain.models.userprofile.enums.HealthNeed;
import com.example.smartmoney.domain.models.userprofile.enums.IncomeLevel;
import com.example.smartmoney.domain.models.userprofile.enums.LivingStatus;
import com.example.smartmoney.domain.models.userprofile.enums.SpendingStyle;
import com.example.smartmoney.domain.models.userprofile.enums.StudyIntensity;
import com.example.smartmoney.domain.models.userprofile.enums.TransportMode;
import com.example.smartmoney.domain.models.userprofile.enums.UserRole;
import com.example.smartmoney.domain.models.userprofile.enums.WorkStyle;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Setter
public class UserFinancialProfile {

    private UUID id;
    private UUID userId;

    private UserRole role;
    private LivingStatus livingStatus;
    private IncomeLevel incomeLevel;
    private TransportMode transportMode;
    private SpendingStyle spendingStyle;
    private WorkStyle workStyle;
    private FamilyStatus familyStatus;
    private StudyIntensity studyIntensity;
    private HealthNeed healthNeed;

    private Instant createdAt;
    private Instant updatedAt;

    public static UserFinancialProfile create(
            UUID userId,
            UserRole role,
            LivingStatus livingStatus,
            IncomeLevel incomeLevel,
            TransportMode transportMode,
            SpendingStyle spendingStyle,
            WorkStyle workStyle,
            FamilyStatus familyStatus,
            StudyIntensity studyIntensity,
            HealthNeed healthNeed
    ) {
        if (userId == null) {
            throw new DomainException("UserId cannot be null");
        }
        if (role == null) {
            throw new DomainException("UserRole cannot be null");
        }
        if (livingStatus == null) {
            throw new DomainException("LivingStatus cannot be null");
        }
        if (incomeLevel == null) {
            throw new DomainException("IncomeLevel cannot be null");
        }
        if (transportMode == null) {
            throw new DomainException("TransportMode cannot be null");
        }
        if (spendingStyle == null) {
            throw new DomainException("SpendingStyle cannot be null");
        }
        if (workStyle == null) {
            throw new DomainException("WorkStyle cannot be null");
        }
        if (familyStatus == null) {
            throw new DomainException("FamilyStatus cannot be null");
        }
        if (studyIntensity == null) {
            throw new DomainException("StudyIntensity cannot be null");
        }
        if (healthNeed == null) {
            throw new DomainException("HealthNeed cannot be null");
        }

        Instant now = Instant.now();

        UserFinancialProfile profile = new UserFinancialProfile();

        // Không set id ở create nếu Entity đang dùng @GeneratedValue
        profile.setUserId(userId);
        profile.setRole(role);
        profile.setLivingStatus(livingStatus);
        profile.setIncomeLevel(incomeLevel);
        profile.setTransportMode(transportMode);
        profile.setSpendingStyle(spendingStyle);
        profile.setWorkStyle(workStyle);
        profile.setFamilyStatus(familyStatus);
        profile.setStudyIntensity(studyIntensity);
        profile.setHealthNeed(healthNeed);
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);

        return profile;
    }

    public void update(
            UserRole role,
            LivingStatus livingStatus,
            IncomeLevel incomeLevel,
            TransportMode transportMode,
            SpendingStyle spendingStyle,
            WorkStyle workStyle,
            FamilyStatus familyStatus,
            StudyIntensity studyIntensity,
            HealthNeed healthNeed
    ) {
        if (role != null) {
            this.role = role;
        }
        if (livingStatus != null) {
            this.livingStatus = livingStatus;
        }
        if (incomeLevel != null) {
            this.incomeLevel = incomeLevel;
        }
        if (transportMode != null) {
            this.transportMode = transportMode;
        }
        if (spendingStyle != null) {
            this.spendingStyle = spendingStyle;
        }
        if (workStyle != null) {
            this.workStyle = workStyle;
        }
        if (familyStatus != null) {
            this.familyStatus = familyStatus;
        }
        if (studyIntensity != null) {
            this.studyIntensity = studyIntensity;
        }
        if (healthNeed != null) {
            this.healthNeed = healthNeed;
        }

        this.updatedAt = Instant.now();
    }
}