package com.example.smartmoney.application.dtos.responses;

import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Focus_Mode;
import com.example.smartmoney.domain.models.user.enums.Intervention_Level;
import com.example.smartmoney.domain.models.user.enums.Saving_Pace;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinancialSetupResponse {

    private BigDecimal income;
    private Saving_Pace savingPace;
    private Intervention_Level interventionLevel;
    private Focus_Mode focusMode;
    private boolean financialSetupCompleted;

    public static FinancialSetupResponse fromDomain(User user) {
        return new FinancialSetupResponse(
                user.getIncome(),
                user.getSavingPace(),
                user.getInterventionLevel(),
                user.getFocusMode(),
                user.hasCompletedFinancialSetup()
        );
    }
}
