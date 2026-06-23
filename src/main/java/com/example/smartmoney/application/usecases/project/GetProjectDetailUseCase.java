package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.responses.project.ProjectDetailResponse;
import com.example.smartmoney.application.dtos.responses.project.ProjectHistoryResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.JoinStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
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

    @Transactional(readOnly = true)
    public ProjectDetailResponse execute(String actorId, String projectId) {
        Project p = projectRepository.findById(ProjectId.of(projectId))
                .orElseThrow(() -> new ProjectException("Project not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        // access check (không có port -> check basic ở đây)
        UserId actor = UserId.of(actorId);
        if (!actor.equals(p.getOwnerId())) {
            if (p.getType() == ProjectType.GROUP) {
                boolean joined = p.getMembers().stream()
                        .anyMatch(m -> m.getUserId().equals(actor) && m.getJoinStatus() == JoinStatus.JOINED);
                if (!joined) {
                    throw new ProjectException("Permission denied", ProjectErrorCode.PROJECT_PERMISSION_DENIED);
                }
            } else {
                throw new ProjectException("Permission denied", ProjectErrorCode.PROJECT_PERMISSION_DENIED);
            }
        }

        List<ProjectHistoryResponse> histories = projectHistoryRepository.findAllByProjectId(p.getId())
                .stream()
                .map(ProjectHistoryResponse::fromDomain)
                .toList();

        return ProjectDetailResponse.fromDomain(p, histories);
    }
}