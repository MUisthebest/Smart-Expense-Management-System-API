package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.dtos.responses.group.GroupProjectDetailResponse;
import com.example.smartmoney.application.dtos.responses.group.GroupProjectMemberProgressResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.GroupProject;
import com.example.smartmoney.domain.models.group.enums.GroupMemberInviteStatus;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.group.valueobjects.GroupProjectId;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.GroupRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetGroupProjectDetailUseCase {

    private final GroupProjectRepository groupProjectRepository;
    private final GroupRepository groupRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
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

        List<Project> subProjects = projectRepository.findAllByGroupProjectId(gp.getId().value());

        BigDecimal aggregate = subProjects.stream()
                .map(p -> p.getTracking() != null ? p.getTracking().getMoneySaved() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal progress = BigDecimal.ZERO;
        if (gp.getTargetAmount().compareTo(BigDecimal.ZERO) > 0) {
            progress = aggregate.multiply(BigDecimal.valueOf(100))
                    .divide(gp.getTargetAmount(), 2, RoundingMode.HALF_UP)
                    .min(BigDecimal.valueOf(100));
        }

        // Batch-load usernames to avoid N+1 — one query for all sub-project owners
        Set<UUID> ownerIds = subProjects.stream()
                .map(p -> UUID.fromString(p.getOwnerId().toString()))
                .collect(Collectors.toSet());
        Map<String, String> usernameByUserId = userRepository.findAllByIds(ownerIds).stream()
                .collect(Collectors.toMap(
                        u -> u.getId().toString(),
                        u -> u.getUsername(),
                        (a, b) -> a
                ));

        List<GroupProjectMemberProgressResponse> memberProgress = new ArrayList<>();
        for (Project sub : subProjects) {
            BigDecimal saved = sub.getTracking() != null ? sub.getTracking().getMoneySaved() : BigDecimal.ZERO;
            BigDecimal target = sub.getTargetAmount().amount();
            BigDecimal pct = target.compareTo(BigDecimal.ZERO) > 0
                    ? saved.multiply(BigDecimal.valueOf(100)).divide(target, 2, RoundingMode.HALF_UP).min(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
            String username = usernameByUserId.get(sub.getOwnerId().toString());
            memberProgress.add(GroupProjectMemberProgressResponse.builder()
                    .userId(sub.getOwnerId().toString())
                    .username(username)
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
