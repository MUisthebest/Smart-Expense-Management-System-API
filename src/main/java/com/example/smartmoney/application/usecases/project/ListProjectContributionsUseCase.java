package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.responses.project.ProjectContributionResponse;
import com.example.smartmoney.domain.models.project.ProjectContribution;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.JoinStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
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
    private final ContributionMapper contributionMapper = new ContributionMapper();

    @Transactional(readOnly = true)
    public List<ProjectContributionResponse> execute(String actorId, String projectId) {
        Project p = projectRepository.findById(ProjectId.of(projectId))
                .orElseThrow(() -> new ProjectException("Project not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        UserId actor = UserId.of(actorId);
        if (!actor.equals(p.getOwnerId())) {
            if (p.getType() == ProjectType.GROUP) {
                boolean joined = p.getMembers().stream()
                        .anyMatch(m -> m.getUserId().equals(actor) && m.getJoinStatus() == JoinStatus.JOINED);
                if (!joined) throw new ProjectException("Permission denied", ProjectErrorCode.PROJECT_PERMISSION_DENIED);
            } else {
                throw new ProjectException("Permission denied", ProjectErrorCode.PROJECT_PERMISSION_DENIED);
            }
        }


        return p.getContributions().stream()
                .sorted(
                        Comparator.comparing((ProjectContribution c) -> c.getContributionDate()).reversed()
                                .thenComparing(c -> c.getCreatedAt(), Comparator.reverseOrder())
                )
                .map(contributionMapper::toResponse)
                .toList();
    }
}
