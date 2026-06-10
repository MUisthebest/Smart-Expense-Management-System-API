package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.requests.project.AcceptInviteRequest;
import com.example.smartmoney.application.dtos.responses.project.ProjectDetailResponse;
import com.example.smartmoney.application.ports.invite.InviteTokenPort;
import com.example.smartmoney.application.ports.invite.InviteTokenPort.InviteTokenPayload;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Redeems a one-time project invite token.
 *
 * <ol>
 *   <li>Validates the token against Redis — rejects if expired or already used.</li>
 *   <li>Accepts the invite in the {@code project_members} row (sets {@code JOIN_STATUS = JOINED}).</li>
 *   <li>Deletes the token from Redis to prevent replay attacks.</li>
 * </ol>
 *
 * <p>The invitee submits the token they received via push notification or email, together
 * with an optional {@code commitmentAmount} (reserved for future billing integration).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcceptProjectInviteUseCase {

    private final ProjectRepository projectRepository;
    private final InviteTokenPort inviteTokenPort;

    @Transactional
    public ProjectDetailResponse execute(String actorId, AcceptInviteRequest req) {

        // 1. Validate the token from Redis
        InviteTokenPayload payload = inviteTokenPort.find(req.getToken())
                .orElseThrow(() -> new ProjectException(
                        "Invite token is invalid or has expired",
                        ProjectErrorCode.PROJECT_NOT_FOUND));

        String projectId = payload.projectId().toString();

        // 2. Load the project
        Project project = projectRepository.findById(ProjectId.of(projectId))
                .orElseThrow(() -> new ProjectException("Project not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        // 3. Accept the invite — domain model sets JoinStatus → JOINED
        project.acceptInvite(UserId.of(actorId));

        Project saved = projectRepository.save(project);

        // 4. Burn the token so it cannot be reused
        inviteTokenPort.delete(req.getToken());

        log.info("[AcceptInvite] User {} accepted invite for project {} (token burned)", actorId, projectId);

        return ProjectDetailResponse.fromDomain(saved);
    }
}
