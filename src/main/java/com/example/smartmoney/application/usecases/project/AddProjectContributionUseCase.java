package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.requests.project.AddProjectContributionRequest;
import com.example.smartmoney.application.dtos.responses.project.ProjectContributionResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.application.usecases.group.NotifyGroupProjectCompletedUseCase;
import com.example.smartmoney.domain.models.group.GroupProject;
import com.example.smartmoney.domain.models.group.enums.GroupProjectStatus;
import com.example.smartmoney.domain.models.group.valueobjects.GroupProjectId;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectContribution;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddProjectContributionUseCase {

    private final ProjectRepository projectRepository;
    private final GroupProjectRepository groupProjectRepository;
    private final NotifyGroupProjectCompletedUseCase notifyGroupProjectCompleted;

    @Transactional
    public ProjectContributionResponse execute(String actorId, String projectId, AddProjectContributionRequest req) {
        Project p = projectRepository.findById(ProjectId.of(projectId))
                .orElseThrow(() -> new ProjectException("Project not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        ProjectContribution c = p.addContribution(
            UserId.of(actorId),
            req.getAmount(),
            req.getNote()
        );

        projectRepository.save(p);

        // If this is a sub-personal project, check whether the parent group project has hit its target
        if (p.isSubPersonal()) {
            checkAndAutoCompleteGroupProject(p.getGroupProjectId());
        }

        return ProjectContributionResponse.fromDomain(c);
    }

    private void checkAndAutoCompleteGroupProject(UUID groupProjectId) {
        try {
            GroupProject gp = groupProjectRepository
                    .findById(GroupProjectId.of(groupProjectId.toString()))
                    .orElse(null);
            if (gp == null || gp.getStatus() != GroupProjectStatus.ACTIVE) return;

            List<Project> subProjects = projectRepository.findAllByGroupProjectId(groupProjectId);
            BigDecimal aggregate = subProjects.stream()
                    .map(p -> p.getTracking() != null ? p.getTracking().getMoneySaved() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (aggregate.compareTo(gp.getTargetAmount()) >= 0) {
                gp.markCompleted();
                groupProjectRepository.save(gp);
                log.info("[GroupProject] Auto-completed group project {} — aggregate {} >= target {}",
                        groupProjectId, aggregate, gp.getTargetAmount());
                notifyGroupProjectCompleted.notifyCompleted(gp);
            }
        } catch (Exception ex) {
            log.warn("[GroupProject] Auto-complete check failed for group project {}: {}",
                    groupProjectId, ex.getMessage());
        }
    }
}
