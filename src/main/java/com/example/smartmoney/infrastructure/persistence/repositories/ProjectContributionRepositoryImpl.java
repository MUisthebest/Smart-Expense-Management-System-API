package com.example.smartmoney.infrastructure.persistence.repositories;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import com.example.smartmoney.domain.repositories.ProjectContributionRepository;
import com.example.smartmoney.infrastructure.persistence.mappers.ProjectContributionMapper;
import com.example.smartmoney.domain.models.project.ProjectContribution;
import com.example.smartmoney.infrastructure.persistence.entities.ProjectContributionEntity;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;


@Repository
@RequiredArgsConstructor
public class ProjectContributionRepositoryImpl
        implements ProjectContributionRepository {

    private final JpaProjectContributionRepository jpaRepo;
    private final ProjectContributionMapper mapper;

    @Override
    public Map<UUID, List<ProjectContribution>> findByProjectIds(
            List<UUID> projectIds
    ) {

        return jpaRepo.findByProjectIdIn(projectIds)
                .stream()
                .collect(Collectors.groupingBy(
                        ProjectContributionEntity::getProjectId,
                        Collectors.mapping(
                                mapper::toDomain,
                                Collectors.toList()
                        )
                ));
    }
}