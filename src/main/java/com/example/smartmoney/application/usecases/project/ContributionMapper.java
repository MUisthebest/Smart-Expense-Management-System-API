package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.responses.project.ProjectContributionResponse;
import com.example.smartmoney.domain.models.project.ProjectContribution;
import org.springframework.stereotype.Component;

@Component
public class ContributionMapper {

    public ProjectContributionResponse toResponse(ProjectContribution c) {
        return ProjectContributionResponse.builder()
                .contributionId(c.getId().toString())
                .userId(c.getUserId().toString())
                .amount(c.getMoney().amount())
                .currency(c.getMoney().currency())
                .contributionDate(c.getContributionDate())
                .note(c.getNote())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
