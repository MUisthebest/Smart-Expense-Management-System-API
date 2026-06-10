package com.example.smartmoney.infrastructure.persistence.mappers;

import com.example.smartmoney.domain.models.project.ProjectMember;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.infrastructure.persistence.entities.ProjectMemberEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps {@link ProjectMember} ↔ {@link ProjectMemberEntity}. Both directions
 * are written explicitly to avoid MapStruct ambiguity introduced by the
 * V13 fields (monthly commitment, arrears balance) — these need to flow
 * through the rich 5-arg domain constructor.
 */
@Component
@Mapper(componentModel = "spring")
public abstract class ProjectMemberMapper {

    /** Hand-rolled to ensure the 5-arg domain constructor is used. */
    public ProjectMember toDomain(ProjectMemberEntity entity) {
        if (entity == null) return null;
        BigDecimal commitment = entity.getMonthlyCommitmentAmount() != null
                ? entity.getMonthlyCommitmentAmount() : BigDecimal.ZERO;
        BigDecimal arrears = entity.getArrearsBalance() != null
                ? entity.getArrearsBalance() : BigDecimal.ZERO;
        return new ProjectMember(
                mapUserIdToDomain(entity.getUserId()),
                entity.getJoinStatus(),
                entity.isAdmin(),
                commitment,
                arrears
        );
    }

    /** Letting MapStruct generate the shell; @AfterMapping fills value objects. */
    public abstract ProjectMemberEntity toEntity(ProjectMember domain);

    protected UUID mapUserIdToEntity(UserId userId) {
        return userId != null ? userId.value() : null;
    }

    protected UserId mapUserIdToDomain(UUID userId) {
        return userId != null ? new UserId(userId) : null;
    }

    @org.mapstruct.AfterMapping
    protected void afterMappingToEntity(@MappingTarget ProjectMemberEntity entity, ProjectMember domain) {
        if (entity != null && domain != null) {
            entity.setUserId(mapUserIdToEntity(domain.getUserId()));
            entity.setJoinStatus(domain.getJoinStatus());
            entity.setAdmin(domain.isAdmin());
            entity.setMonthlyCommitmentAmount(
                    domain.getMonthlyCommitmentAmount() != null
                            ? domain.getMonthlyCommitmentAmount() : BigDecimal.ZERO);
            entity.setArrearsBalance(
                    domain.getArrearsBalance() != null
                            ? domain.getArrearsBalance() : BigDecimal.ZERO);
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

