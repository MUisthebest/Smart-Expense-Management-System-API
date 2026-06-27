package com.example.smartmoney.domain.repositories;
import com.example.smartmoney.domain.models.project.ProjectContribution;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ProjectContributionRepository {
    Map<UUID, List<ProjectContribution>> findByProjectIds(List<UUID> projectIds);
}