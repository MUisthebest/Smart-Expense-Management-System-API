package com.example.smartmoney.infrastructure.persistence.mappers;

import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.infrastructure.persistence.entities.ProjectTrackingEntity;
import org.springframework.stereotype.Component;

@Component
public class ProjectTrackingMapper {

    public ProjectTracking toDomain(ProjectTrackingEntity entity) {
        if (entity == null) {
            return null;
        }

        return new ProjectTracking(
                entity.getId(),
                new ProjectId(entity.getProjectId()),
                entity.getMoneySaved(),
                entity.getCurrentMonth(),
                entity.getMonthLeft(),
                entity.getMoneyOwed(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public ProjectTrackingEntity toEntity(ProjectTracking domain) {
        return ProjectTrackingEntity.builder()
                .id(domain.getId())
                .projectId(domain.getProjectId().value())
                .moneySaved(domain.getMoneySaved())
                .currentMonth(domain.getCurrentMonth())
                .monthLeft(domain.getMonthLeft())
                .moneyOwed(domain.getMoneyOwed())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }
}
