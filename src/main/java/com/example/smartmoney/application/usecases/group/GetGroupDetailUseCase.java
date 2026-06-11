package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.dtos.responses.group.GroupDetailResponse;
import com.example.smartmoney.application.dtos.responses.group.GroupMemberResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.enums.GroupMemberInviteStatus;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.GroupRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetGroupDetailUseCase {

    private final GroupRepository groupRepository;
    private final GroupProjectRepository groupProjectRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public GroupDetailResponse execute(String actorId, String groupId) {
        Group group = groupRepository.findById(GroupId.of(groupId))
                .orElseThrow(() -> new ProjectException("Group not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        UserId actor = UserId.of(actorId);
        boolean canView = actor.equals(group.getAdminId())
                || group.getMembers().stream().anyMatch(m ->
                        m.getUserId().equals(actor)
                        && m.getInviteStatus() == GroupMemberInviteStatus.JOINED);
        if (!canView) {
            throw new ProjectException("Permission denied", ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }

        String groupProjectId = groupProjectRepository.findByGroupId(group.getId().value())
                .map(gp -> gp.getId().toString())
                .orElse(null);

        // Batch-load usernames for all members
        Map<String, String> usernameByUserId = group.getMembers().stream()
                .collect(Collectors.toMap(
                        m -> m.getUserId().toString(),
                        m -> userRepository.findById(UUID.fromString(m.getUserId().toString()))
                                .map(u -> u.getUsername())
                                .orElse(null),
                        (a, b) -> a
                ));

        List<GroupMemberResponse> memberResponses = group.getMembers().stream()
                .map(m -> GroupMemberResponse.fromDomain(m, usernameByUserId.get(m.getUserId().toString())))
                .toList();

        return GroupDetailResponse.builder()
                .groupId(group.getId().toString())
                .name(group.getName())
                .description(group.getDescription())
                .adminId(group.getAdminId().toString())
                .status(group.getStatus())
                .groupProjectId(groupProjectId)
                .members(memberResponses)
                .createdAt(group.getCreatedAt())
                .build();
    }
}
