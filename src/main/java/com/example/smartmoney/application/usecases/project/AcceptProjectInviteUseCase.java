package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.responses.project.ProjectDetailResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectMember;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.services.SafeSpendingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AcceptProjectInviteUseCase {

    private final ProjectRepository projectRepository;
    private final SafeSpendingService safeSpendingService;

    @Transactional
    public ProjectDetailResponse execute(String actorId, String projectId) {
        Project p = projectRepository.findById(ProjectId.of(projectId))
                .orElseThrow(() -> new ProjectException("Project not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        UserId invitee = UserId.of(actorId);

        // Privacy-preserving safe-spending precondition. The InsufficientSafeSpendingException
        // is only ever surfaced via this endpoint, which is invoked by the invitee themselves —
        // the admin who issued the invite never sees the financial details.
        ProjectMember member = p.getMembers().stream()
                .filter(m -> invitee.equals(m.getUserId()))
                .findFirst()
                .orElseThrow(() -> new ProjectException("Invite not found",
                        ProjectErrorCode.PROJECT_MEMBER_NOT_FOUND));

        BigDecimal commitment = member.getMonthlyCommitmentAmount();
        if (commitment != null && commitment.compareTo(BigDecimal.ZERO) > 0) {
            safeSpendingService.requireCanAfford(invitee, commitment);
        }

        p.acceptInvite(invitee);
        Project saved = projectRepository.save(p);

        // Recalculate invitee's safe spending so their commitments include
        // the new project obligation going forward.
        safeSpendingService.recalculateAndSaveSafeSpending(invitee);

        return ProjectDetailResponse.fromDomain(saved);
    }
}
