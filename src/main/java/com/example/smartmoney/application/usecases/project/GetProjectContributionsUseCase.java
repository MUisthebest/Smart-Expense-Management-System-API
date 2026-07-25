package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.responses.project.ContributionItemResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectContribution;
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
public class GetProjectContributionsUseCase {

    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public List<ContributionItemResponse> execute(String actorId, String projectId) {
        Project project = projectRepository.findById(ProjectId.of(projectId))
                .orElseThrow(() -> new ProjectException("Project not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        ProjectAccessPolicy.requireCanView(project, UserId.of(actorId));

        return project.getContributions().stream()
                .sorted(Comparator.comparing(ProjectContribution::getCreatedAt).reversed())
                .map(ContributionItemResponse::fromDomain)
                .toList();
    }
}
