package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.requests.project.SetProjectAdminRequest;
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

@Service
@RequiredArgsConstructor
public class SetProjectAdminUseCase {

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper = new ProjectMapper();

    @Transactional
    public ProjectDetailResponse execute(String actorId, String projectId, String userId, SetProjectAdminRequest req) {
        Project p = projectRepository.findById(ProjectId.of(projectId))
                .orElseThrow(() -> new ProjectException("Project not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        p.setAdmin(UserId.of(actorId), UserId.of(userId), req.isAdmin());

        Project saved = projectRepository.save(p);
        return projectMapper.toDetail(saved);
    }
}
