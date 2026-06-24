package com.example.smartmoney.application.dtos.responses.group;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class GroupProjectMemberProgressResponse {
    private String userId;
    private String username;
    private String personalProjectId;
    private BigDecimal moneySaved;   // gross reserved
    private BigDecimal moneyOwed;    // overspend debt
    private BigDecimal netSaved;     // moneySaved − moneyOwed (drives progress)
    private BigDecimal targetAmount;
    private BigDecimal progressPercent;
    private String projectStatus;
}
