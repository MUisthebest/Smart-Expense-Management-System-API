package com.example.smartmoney.application.dtos.responses.group;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class GroupProjectSponsorshipRequestResponse {
    private String requestId;
    private String groupProjectId;
    private String groupProjectName;
    private String groupName;
    private BigDecimal askedAmount;
    private BigDecimal originalShare;
    private BigDecimal proposedShare;
    private Integer totalMonths;
}
