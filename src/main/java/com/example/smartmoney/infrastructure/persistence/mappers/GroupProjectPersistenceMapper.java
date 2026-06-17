package com.example.smartmoney.infrastructure.persistence.mappers;

import com.example.smartmoney.domain.models.group.GroupProject;
import com.example.smartmoney.domain.models.group.valueobjects.GroupProjectId;
import com.example.smartmoney.infrastructure.persistence.entities.GroupProjectEntity;
import org.springframework.stereotype.Component;

@Component
public class GroupProjectPersistenceMapper {

    public GroupProject toDomain(GroupProjectEntity e) {
        return GroupProject.rehydrate(
                new GroupProjectId(e.getId()),
                e.getGroupId(),
                e.getName(),
                e.getDescription(),
                e.getTargetAmount(),
                e.getTotalCapacity(),
                e.getCurrency(),
                e.getTotalMonths(),
                e.getDeadline(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    public GroupProjectEntity toEntity(GroupProject gp) {
        return GroupProjectEntity.builder()
                .id(gp.getId().value())
                .groupId(gp.getGroupId())
                .name(gp.getName())
                .description(gp.getDescription())
                .targetAmount(gp.getTargetAmount())
                .totalCapacity(gp.getTotalCapacity())
                .currency(gp.getCurrency())
                .totalMonths(gp.getTotalMonths())
                .deadline(gp.getDeadline())
                .status(gp.getStatus())
                .createdAt(gp.getCreatedAt())
                .updatedAt(gp.getUpdatedAt())
                .build();
    }
}
