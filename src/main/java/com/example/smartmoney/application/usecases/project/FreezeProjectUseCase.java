package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.responses.project.ProjectDetailResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner-initiated freeze of a project. Used rarely — the typical trigger is
 * automated via {@code AssessProjectArrearsUseCase} when the owner hits the
 * 3-month default threshold. Exposed as an explicit endpoint for cases where
 * the owner wants to pause contributions deliberately.
 */
@Service
@RequiredArgsConstructor
public class FreezeProjectUseCase {

    private final ProjectRepository projectRepository;

    @Transactional
    public ProjectDetailResponse execute(String actorId, String projectId) {
        Project p = projectRepository.findById(ProjectId.of(projectId))
                .orElseThrow(() -> new ProjectException("Project not found",
                        ProjectErrorCode.PROJECT_NOT_FOUND));
        p.freeze(UserId.of(actorId));
        return ProjectDetailResponse.fromDomain(projectRepository.save(p));
    }
}
