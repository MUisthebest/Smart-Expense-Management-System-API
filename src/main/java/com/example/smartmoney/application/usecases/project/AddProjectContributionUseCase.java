package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.requests.project.AddProjectContributionRequest;
import com.example.smartmoney.application.dtos.responses.project.ProjectContributionResponse;
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

@Service
@RequiredArgsConstructor
public class AddProjectContributionUseCase {

    private final ProjectRepository projectRepository;
    private final ContributionMapper contributionMapper = new ContributionMapper();

    @Transactional
    public ProjectContributionResponse execute(String actorId, String projectId, AddProjectContributionRequest req) {
        Project p = projectRepository.findById(ProjectId.of(projectId))
                .orElseThrow(() -> new ProjectException("Project not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        ProjectContribution c = p.addContribution(
                UserId.of(actorId),
                req.getAmount(),
                req.getContributionDate(),
                req.getNote()
        );

        projectRepository.save(p);
        return contributionMapper.toResponse(c);
    }
}
