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
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.application.dtos.responses.project.ProjectDetailResponse.ProjectMemberResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GetProjectDetailUseCase {

    private final ProjectRepository projectRepository;
    private final ProjectHistoryRepository projectHistoryRepository;
    private final UserRepository userRepository;

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

        List<ProjectMemberResponse> memberResponses = new ArrayList<>();
        if (p.getType() == ProjectType.GROUP) {
            userRepository.findById(p.getOwnerId().value()).ifPresent(owner -> {
                memberResponses.add(ProjectMemberResponse.builder()
                        .userId(owner.getId().toString())
                        .username(owner.getUsername())
                        .email(owner.getEmail() != null ? owner.getEmail().getValue() : "")
                        .fullName(owner.getFullName())
                        .joinStatus("JOINED")
                        .admin(true)
                        .build());
            });

            for (var m : p.getMembers()) {
                if (m.getJoinStatus() != JoinStatus.LEFT) {
                    userRepository.findById(m.getUserId().value()).ifPresent(user -> {
                        memberResponses.add(ProjectMemberResponse.builder()
                                .userId(user.getId().toString())
                                .username(user.getUsername())
                                .email(user.getEmail() != null ? user.getEmail().getValue() : "")
                                .fullName(user.getFullName())
                                .joinStatus(m.getJoinStatus().name())
                                .admin(m.isAdmin())
                                .build());
                    });
                }
            }
        }

        return ProjectDetailResponse.fromDomain(p, histories, memberResponses);
    }
}
