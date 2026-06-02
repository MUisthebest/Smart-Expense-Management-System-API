package com.example.smartmoney.application.dtos.requests.userfinancialprofile;

import com.example.smartmoney.domain.models.userprofile.enums.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserFinancialProfileRequest {

    @NotNull
    private UserRole role;

    @NotNull
    @JsonProperty("living_status")
    private LivingStatus livingStatus;

    @NotNull
    @JsonProperty("income_level")
    private IncomeLevel incomeLevel;

    @NotNull
    @JsonProperty("transport_mode")
    private TransportMode transportMode;

    @NotNull
    @JsonProperty("spending_style")
    private SpendingStyle spendingStyle;

    @NotNull
    @JsonProperty("work_style")
    private WorkStyle workStyle;

    @NotNull
    @JsonProperty("family_status")
    private FamilyStatus familyStatus;

    @NotNull
    @JsonProperty("study_intensity")
    private StudyIntensity studyIntensity;

    @NotNull
    @JsonProperty("health_need")
    private HealthNeed healthNeed;
}