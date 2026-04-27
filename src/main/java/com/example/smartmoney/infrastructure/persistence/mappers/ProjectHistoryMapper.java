package com.example.smartmoney.infrastructure.persistence.mappers;

import com.example.smartmoney.domain.models.project.ProjectHistory;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.infrastructure.persistence.entities.ProjectHistoryEntity;
import org.springframework.stereotype.Component;

@Component
public class ProjectHistoryMapper {

    public ProjectHistory toDomain(ProjectHistoryEntity entity) {
        if (entity == null) return null;
        return ProjectHistory.rehydrate(
                entity.getId(),
                new ProjectId(entity.getProjectId()),
                entity.getUserId(),
                entity.getMonth(),
                entity.getYear(),
                entity.getMoneySavedBefore(),
                entity.getMoneySavedAfter(),
                entity.getMonthlySaving(),
                entity.getPenalty(),
                entity.getSurplusInvested(),
                entity.getMonthLeftBefore(),
                entity.getMonthLeftAfter(),
                entity.getCreatedAt()
        );
    }

    public ProjectHistoryEntity toEntity(ProjectHistory domain) {
        return ProjectHistoryEntity.builder()
                .id(domain.getId())
                .projectId(domain.getProjectId().value())
                .userId(domain.getUserId())
                .month(domain.getMonth())
                .year(domain.getYear())
                .moneySavedBefore(domain.getMoneySavedBefore())
                .moneySavedAfter(domain.getMoneySavedAfter())
                .monthlySaving(domain.getMonthlySaving())
                .penalty(domain.getPenalty())
                .surplusInvested(domain.getSurplusInvested())
                .monthLeftBefore(domain.getMonthLeftBefore())
                .monthLeftAfter(domain.getMonthLeftAfter())
                .createdAt(domain.getCreatedAt())
                .build();
    }
}
