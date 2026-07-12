package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.responses.project.ProjectDetailResponse;
import com.example.smartmoney.application.dtos.responses.project.ProjectHistoryResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectHistoryRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetProjectDetailUseCase {

    private final ProjectRepository projectRepository;
    private final ProjectHistoryRepository projectHistoryRepository;

    @Transactional
    public ProjectDetailResponse execute(String actorId, String projectId) {
        Project p = projectRepository.findById(ProjectId.of(projectId))
                .orElseThrow(() -> new ProjectException("Project not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        UserId actor = UserId.of(actorId);
        if (!actor.equals(p.getOwnerId())) {
            throw new ProjectException("Permission denied", ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }

        // Lazy-on-read expiry (replaces the settlement cron's expiry pass).
        if (p.expireIfDeadlinePassed()) {
            p = projectRepository.save(p);
        }

        List<ProjectHistoryResponse> histories = projectHistoryRepository.findAllByProjectId(p.getId())
                .stream()
                .map(ProjectHistoryResponse::fromDomain)
                .toList();

        return ProjectDetailResponse.fromDomain(p, histories);
    }
}
