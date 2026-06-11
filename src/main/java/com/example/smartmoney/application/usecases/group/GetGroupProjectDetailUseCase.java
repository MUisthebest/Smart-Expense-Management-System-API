package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.dtos.responses.group.GroupProjectDetailResponse;
import com.example.smartmoney.application.dtos.responses.group.GroupProjectMemberProgressResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.GroupProject;
import com.example.smartmoney.domain.models.group.enums.GroupMemberInviteStatus;
import com.example.smartmoney.domain.models.group.enums.GroupProjectStatus;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.group.valueobjects.GroupProjectId;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.GroupRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GetGroupProjectDetailUseCase {

    private final GroupProjectRepository groupProjectRepository;
    private final GroupRepository groupRepository;
    private final ProjectRepository projectRepository;

    @Transactional
    public GroupProjectDetailResponse execute(String actorId, String groupProjectId) {
        GroupProject gp = groupProjectRepository.findById(GroupProjectId.of(groupProjectId))
                .orElseThrow(() -> new ProjectException("Group project not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        Group group = groupRepository.findById(GroupId.of(gp.getGroupId().toString()))
                .orElseThrow(() -> new ProjectException("Group not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        UserId actor = UserId.of(actorId);
        boolean canView = actor.equals(group.getAdminId())
                || group.getMembers().stream().anyMatch(m ->
                        m.getUserId().equals(actor)
                        && m.getInviteStatus() == GroupMemberInviteStatus.JOINED);
        if (!canView) {
            throw new ProjectException("Permission denied", ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }

        List<Project> subProjects = projectRepository.findAllByGroupProjectId(
                gp.getId().value());

        BigDecimal aggregate = subProjects.stream()
                .map(p -> p.getTracking() != null ? p.getTracking().getMoneySaved() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal progress = BigDecimal.ZERO;
        if (gp.getTargetAmount().compareTo(BigDecimal.ZERO) > 0) {
            progress = aggregate.multiply(BigDecimal.valueOf(100))
                    .divide(gp.getTargetAmount(), 2, RoundingMode.HALF_UP)
                    .min(BigDecimal.valueOf(100));
        }

        // Auto-complete if aggregate reached target
        if (gp.getStatus() == GroupProjectStatus.ACTIVE
                && aggregate.compareTo(gp.getTargetAmount()) >= 0) {
            gp.markCompleted();
            groupProjectRepository.save(gp);
        }

        List<GroupProjectMemberProgressResponse> memberProgress = new ArrayList<>();
        for (Project sub : subProjects) {
            BigDecimal saved = sub.getTracking() != null ? sub.getTracking().getMoneySaved() : BigDecimal.ZERO;
            BigDecimal target = sub.getTargetAmount().amount();
            BigDecimal pct = target.compareTo(BigDecimal.ZERO) > 0
                    ? saved.multiply(BigDecimal.valueOf(100)).divide(target, 2, RoundingMode.HALF_UP).min(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
            memberProgress.add(GroupProjectMemberProgressResponse.builder()
                    .userId(sub.getOwnerId().toString())
                    .personalProjectId(sub.getId().toString())
                    .moneySaved(saved)
                    .targetAmount(target)
                    .progressPercent(pct)
                    .projectStatus(sub.getStatus().name())
                    .build());
        }

        return GroupProjectDetailResponse.fromDomain(gp, aggregate, progress, memberProgress);
    }
}
