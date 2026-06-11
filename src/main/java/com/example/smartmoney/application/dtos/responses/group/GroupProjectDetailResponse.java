package com.example.smartmoney.application.dtos.responses.group;

import com.example.smartmoney.domain.models.group.GroupProject;
import com.example.smartmoney.domain.models.group.enums.GroupProjectStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class GroupProjectDetailResponse {
    private String groupProjectId;
    private String groupId;
    private String name;
    private String description;
    private BigDecimal targetAmount;
    private BigDecimal totalCapacity;
    private String currency;
    private Integer totalMonths;
    private LocalDate deadline;
    private GroupProjectStatus status;
    private BigDecimal aggregateMoneySaved;
    private BigDecimal progressPercent;
    private List<GroupProjectMemberProgressResponse> members;

    public static GroupProjectDetailResponse fromDomain(GroupProject gp,
                                                        BigDecimal aggregateMoneySaved,
                                                        BigDecimal progressPercent,
                                                        List<GroupProjectMemberProgressResponse> members) {
        return GroupProjectDetailResponse.builder()
                .groupProjectId(gp.getId().toString())
                .groupId(gp.getGroupId().toString())
                .name(gp.getName())
                .description(gp.getDescription())
                .targetAmount(gp.getTargetAmount())
                .totalCapacity(gp.getTotalCapacity())
                .currency(gp.getCurrency())
                .totalMonths(gp.getTotalMonths())
                .deadline(gp.getDeadline())
                .status(gp.getStatus())
                .aggregateMoneySaved(aggregateMoneySaved)
                .progressPercent(progressPercent)
                .members(members)
                .build();
    }
}
