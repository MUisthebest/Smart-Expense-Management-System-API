package com.example.smartmoney.application.dtos.responses;

/*
import com.example.smartmoney.infrastructure.persistence.entities.UserFinancialProfileEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class UserFinancialProfileResponse {
    private UUID id;
    private UUID userId;

    private String role;

    @JsonProperty("living_status")
    private String livingStatus;

    @JsonProperty("income_level")
    private String incomeLevel;

    @JsonProperty("transport_mode")
    private String transportMode;

    @JsonProperty("spending_style")
    private String spendingStyle;

    @JsonProperty("work_style")
    private String workStyle;

    @JsonProperty("family_status")
    private String familyStatus;

    @JsonProperty("study_intensity")
    private String studyIntensity;

    @JsonProperty("health_need")
    private String healthNeed;

    private Instant createdAt;
    private Instant updatedAt;

    public static UserFinancialProfileResponse fromEntity(UserFinancialProfileEntity entity) {
        return UserFinancialProfileResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .role(entity.getRole().name())
                .livingStatus(entity.getLivingStatus().name())
                .incomeLevel(entity.getIncomeLevel().name())
                .transportMode(entity.getTransportMode().name())
                .spendingStyle(entity.getSpendingStyle().name())
                .workStyle(entity.getWorkStyle().name())
                .familyStatus(entity.getFamilyStatus().name())
                .studyIntensity(entity.getStudyIntensity().name())
                .healthNeed(entity.getHealthNeed().name())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
*/
