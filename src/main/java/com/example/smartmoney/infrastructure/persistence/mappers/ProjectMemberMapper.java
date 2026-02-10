package com.example.smartmoney.infrastructure.persistence.mappers;

import com.example.smartmoney.domain.models.project.ProjectMember;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.infrastructure.persistence.entities.ProjectMemberEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@Mapper(componentModel = "spring")
public abstract class ProjectMemberMapper {

    public abstract ProjectMemberEntity toEntity(ProjectMember domain);

    @Mapping(target = "userId", expression = "java(mapUserIdToDomain(entity.getUserId()))")
    public abstract ProjectMember toDomain(ProjectMemberEntity entity);

    protected Long mapUserIdToEntity(UserId userId) {
        return userId != null ? userId.value() : null;
    }

    protected UserId mapUserIdToDomain(Long userId) {
        return userId != null ? new UserId(userId) : null;
    }

    @org.mapstruct.AfterMapping
    protected void afterMappingToEntity(@MappingTarget ProjectMemberEntity entity, ProjectMember domain) {
        if (entity != null && domain != null) {
            entity.setUserId(mapUserIdToEntity(domain.getUserId()));
            entity.setJoinStatus(domain.getJoinStatus());
            entity.setAdmin(domain.isAdmin());
        }
    }

    public ProjectMemberEntity toEntity(ProjectMember domain, UUID projectId, Instant now) {
        ProjectMemberEntity e = toEntity(domain);
        e.setId(UUID.randomUUID());
        e.setProjectId(projectId);
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }
}
