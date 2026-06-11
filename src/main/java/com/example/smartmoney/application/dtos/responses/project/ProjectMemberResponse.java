package com.example.smartmoney.application.dtos.responses.project;

import com.example.smartmoney.domain.models.project.ProjectMember;
import com.example.smartmoney.domain.models.project.enums.JoinStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Per-member view used inside {@link ProjectDetailResponse} so clients can
 * render the V13 commitment / arrears columns alongside membership state.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMemberResponse {
    private String userId;
    private String username;
    private String email;
    private String fullName;
    private JoinStatus joinStatus;
    private boolean admin;
    private BigDecimal monthlyCommitmentAmount;
    private BigDecimal arrearsBalance;

    public static ProjectMemberResponse fromDomain(ProjectMember m) {
        return ProjectMemberResponse.builder()
                .userId(m.getUserId().toString())
                .joinStatus(m.getJoinStatus())
                .admin(m.isAdmin())
                .monthlyCommitmentAmount(m.getMonthlyCommitmentAmount() != null
                        ? m.getMonthlyCommitmentAmount() : BigDecimal.ZERO)
                .arrearsBalance(m.getArrearsBalance() != null
                        ? m.getArrearsBalance() : BigDecimal.ZERO)
                .build();
    }
}
