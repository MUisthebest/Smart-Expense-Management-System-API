package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.responses.project.ProjectContributionResponse;
import com.example.smartmoney.domain.models.project.ProjectContribution;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ListProjectContributionsUseCase {

    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public List<ProjectContributionResponse> execute(String actorId, String projectId) {
        Project p = projectRepository.findById(ProjectId.of(projectId))
                .orElseThrow(() -> new ProjectException("Project not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        UserId actor = UserId.of(actorId);
        if (!actor.equals(p.getOwnerId())) {
            throw new ProjectException("Permission denied", ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }

        return p.getContributions().stream()
                .sorted(Comparator.comparing(ProjectContribution::getCreatedAt).reversed())
                .map(ProjectContributionResponse::fromDomain)
                .toList();
    }
}
