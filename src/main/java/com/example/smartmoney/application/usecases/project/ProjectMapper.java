package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.responses.project.ProjectDetailResponse;
import com.example.smartmoney.application.dtos.responses.project.ProjectListItemResponse;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import org.springframework.stereotype.Component;

@Component
public class ProjectMapper {

    public ProjectDetailResponse toDetail(Project p) {
        Money total = p.totalContributed();
        Money remaining = p.remaining();

        return ProjectDetailResponse.builder()
                .projectId(p.getId().toString())
                .ownerId(p.getOwnerId().toString())
                .type(p.getType())
                .name(p.getName())
                .description(p.getDescription())
                .targetAmount(p.getTargetAmount().amount())
                .currency(p.getTargetAmount().currency())
                .totalContributed(total.amount())
                .remaining(remaining.amount())
                .progressPercent(p.progressPercent())
                .deadline(p.getDeadline())
                .build();
    }

    public ProjectListItemResponse toListItem(Project p) {
        Money total = p.totalContributed();

        return ProjectListItemResponse.builder()
                .projectId(p.getId().toString())
                .name(p.getName())
                .type(p.getType())
                .targetAmount(p.getTargetAmount().amount())
                .currency(p.getTargetAmount().currency())
                .totalContributed(total.amount())
                .progressPercent(p.progressPercent())
                .deadline(p.getDeadline())
                .build();
    }
}
