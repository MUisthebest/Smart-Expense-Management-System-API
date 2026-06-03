package com.example.smartmoney.infrastructure.persistence.mappers;

import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectContribution;
import com.example.smartmoney.domain.models.project.ProjectMember;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.infrastructure.persistence.entities.ProjectContributionEntity;
import com.example.smartmoney.infrastructure.persistence.entities.ProjectEntity;
import com.example.smartmoney.infrastructure.persistence.entities.ProjectMemberEntity;
import com.example.smartmoney.infrastructure.persistence.entities.ProjectTrackingEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProjectPersistenceMapper {

    private final ProjectMemberMapper memberMapper;
    private final ProjectContributionMapper contributionMapper;
    private final ProjectTrackingMapper trackingMapper;

    public Project toDomain(ProjectEntity p,
                            List<ProjectMemberEntity> members,
                            List<ProjectContributionEntity> contributions,
                            ProjectTrackingEntity tracking) {

        List<ProjectMember> dm = members == null ? List.of() : members.stream()
                .map(memberMapper::toDomain)
                .toList();

        List<ProjectContribution> dc = contributions == null ? List.of() : contributions.stream()
                .map(contributionMapper::toDomain)
                .toList();

        ProjectTracking dt = tracking != null ? trackingMapper.toDomain(tracking) : null;

        return Project.rehydrate(
                new ProjectId(p.getId()),
                new UserId(p.getOwnerId()),
                p.getType(),
                p.getStatus(),
                p.getPriority(),
                p.getName(),
                p.getDescription(),
                new Money(p.getTargetAmount(), p.getCurrency()),
                p.getDeadline(),
                dm,
                dc,
                dt
        );
    }
}
