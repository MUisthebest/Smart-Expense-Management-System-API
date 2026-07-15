package com.example.smartmoney.application.dtos.responses.group;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class GroupProjectSuggestionsResponse {
    /** Sum of all JOINED members' capacity snapshots. */
    private BigDecimal totalCapacity;
    /** Minimum months needed to reach inputAmount at totalCapacity per month. */
    private Integer suggestedMonths;
    /** Amount achievable if saving at totalCapacity for inputMonths. */
    private BigDecimal suggestedAmount;

    // Simulation fields
    private Boolean isFeasible;
    private BigDecimal totalDeficit;
    private List<MemberSimulationDto> memberSimulations;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MemberSimulationDto {
        private String userId;
        private String username;
        private String fullName;
        private BigDecimal capacity;
        private BigDecimal originalShare;
        private BigDecimal proposedShare;
        private BigDecimal deficit;
        private Boolean needsSponsorship;
        private Boolean autoSponsorEnabled;
        private BigDecimal autoSponsorLimit;
    }
}
