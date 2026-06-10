package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.requests.project.InviteProjectMemberRequest;
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
public class InviteProjectMemberUseCase {

    private final ProjectRepository projectRepository;

    @Transactional
    public ProjectDetailResponse execute(String actorId, String projectId, InviteProjectMemberRequest req) {
        Project p = projectRepository.findById(ProjectId.of(projectId))
                .orElseThrow(() -> new ProjectException("Project not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        // Note: invitee's safe-spending is intentionally NOT checked here.
        // That check belongs in AcceptProjectInviteUseCase so the admin
        // never sees the invitee's financial state (privacy-preserving).
        p.inviteMember(
                UserId.of(actorId),
                UserId.of(req.getUserId()),
                req.isAdmin(),
                req.getMonthlyCommitmentAmount());

        Project saved = projectRepository.save(p);
        return ProjectDetailResponse.fromDomain(saved);
    }
}

