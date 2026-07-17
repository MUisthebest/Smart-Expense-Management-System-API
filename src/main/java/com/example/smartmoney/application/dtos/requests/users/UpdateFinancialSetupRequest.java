package com.example.smartmoney.application.dtos.requests.users;


import com.example.smartmoney.domain.models.user.enums.Focus_Mode;
import com.example.smartmoney.domain.models.user.enums.Intervention_Level;
import com.example.smartmoney.domain.models.user.enums.Saving_Pace;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateFinancialSetupRequest(

        @NotNull(message = "Income is required")
        @DecimalMin(
                value = "0.01",
                message = "Income must be greater than zero"
        )
        BigDecimal income,

        @NotNull(message = "Pace is required")
        Saving_Pace savingPace,

        @NotNull(message = "Intervention level is required")
        Intervention_Level interventionLevel,

        @NotNull(message = "Focus is required")
        Focus_Mode focusMode,

        // Whether monthly settlement sweeps leftover money into the user's projects.
        // Required: it gates hasCompletedFinancialSetup(), so onboarding must have the
        // user make an explicit choice rather than silently defaulting them to always-invest.
        @NotNull(message = "Auto-invest surplus preference is required")
        Boolean autoInvestSurplus
) {
}
