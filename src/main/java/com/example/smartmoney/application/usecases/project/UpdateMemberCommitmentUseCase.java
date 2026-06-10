package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.requests.project.UpdateMemberCommitmentRequest;
import com.example.smartmoney.application.dtos.responses.project.ProjectDetailResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectMember;
import com.example.smartmoney.domain.models.project.enums.JoinStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.services.SafeSpendingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Admin-only path to change a member's monthly commitment after they've
 * joined. If the new commitment is higher than the previous one, the target
 * member's own safe-spending must cover the delta — same gate as accept.
 */
@Service
@RequiredArgsConstructor
public class UpdateMemberCommitmentUseCase {

    private final ProjectRepository projectRepository;
    private final SafeSpendingService safeSpendingService;

    @Transactional
    public ProjectDetailResponse execute(String actorId,
                                         String projectId,
                                         String targetUserId,
                                         UpdateMemberCommitmentRequest req) {
        Project p = projectRepository.findById(ProjectId.of(projectId))
                .orElseThrow(() -> new ProjectException("Project not found",
                        ProjectErrorCode.PROJECT_NOT_FOUND));

        if (p.getStatus() == ProjectStatus.ABANDONED) {
            throw new ProjectException("Project is abandoned",
                    ProjectErrorCode.PROJECT_ABANDONED);
        }

        UserId actor = UserId.of(actorId);
        UserId target = UserId.of(targetUserId);

        // Authorisation: only owner or an admin member may change commitments.
        boolean isOwner = actor.equals(p.getOwnerId());
        boolean isAdminMember = p.getMembers().stream()
                .anyMatch(m -> actor.equals(m.getUserId())
                        && m.isAdmin()
                        && m.getJoinStatus() == JoinStatus.JOINED);
        if (!isOwner && !isAdminMember) {
            throw new ProjectException("Permission denied",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }

        ProjectMember member = p.getMembers().stream()
                .filter(m -> target.equals(m.getUserId()))
                .findFirst()
                .orElseThrow(() -> new ProjectException("Member not found",
                        ProjectErrorCode.PROJECT_MEMBER_NOT_FOUND));

        BigDecimal oldCommitment = member.getMonthlyCommitmentAmount() == null
                ? BigDecimal.ZERO : member.getMonthlyCommitmentAmount();
        BigDecimal newCommitment = req.getAmount();

        // Gate: if raising, the affected member must be able to absorb the delta.
        BigDecimal delta = newCommitment.subtract(oldCommitment);
        if (delta.compareTo(BigDecimal.ZERO) > 0) {
            safeSpendingService.requireCanAfford(target, delta);
        }

        member.setCommitment(newCommitment);
        Project saved = projectRepository.save(p);

        // Update the target member's safe spending so subsequent decisions
        // reflect the new commitment immediately.
        safeSpendingService.recalculateAndSaveSafeSpending(target);

        return ProjectDetailResponse.fromDomain(saved);
    }
}
