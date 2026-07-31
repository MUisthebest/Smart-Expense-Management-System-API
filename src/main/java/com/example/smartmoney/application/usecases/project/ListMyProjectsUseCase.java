package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.requests.project.ProjectFilterRequest;
import com.example.smartmoney.application.dtos.responses.project.ProjectListItemResponse;
import com.example.smartmoney.application.dtos.responses.project.ProjectParticipantResponse;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListMyProjectsUseCase {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    @Transactional
    public List<ProjectListItemResponse> execute(String actorId, ProjectFilterRequest filter) {
        List<Project> projects =
                projectRepository.findAllByUser(UserId.of(actorId), filter);

        // Lazy-on-read expiry (replaces the settlement cron's expiry pass). Persist any
        // project that just crossed its deadline under target; the in-memory status is
        // already updated, so the mapped response reflects EXPIRED either way.
        projects.forEach(p -> {
            if (p.expireIfDeadlinePassed()) {
                projectRepository.save(p);
            }
        });

        Map<UUID, List<ProjectParticipantResponse>> participantsByGroupProject =
                loadParticipants(projects);

        return projects.stream()
                .map(p -> ProjectListItemResponse.fromDomain(
                        p,
                        p.getGroupProjectId() == null
                                ? null
                                : participantsByGroupProject.getOrDefault(p.getGroupProjectId(), List.of())))
                .toList();
    }

    /**
     * Participants of each group project appearing in the list: the owners of that group
     * project's sub-personal projects (ABANDONED sub-projects mean the member left, so they
     * are excluded). Users are batch-loaded in one query to avoid an N+1 on the list endpoint.
     */
    private Map<UUID, List<ProjectParticipantResponse>> loadParticipants(List<Project> projects) {
        Set<UUID> groupProjectIds = projects.stream()
                .map(Project::getGroupProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (groupProjectIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<Project>> subProjectsByGroup = new HashMap<>();
        for (UUID groupProjectId : groupProjectIds) {
            subProjectsByGroup.put(groupProjectId,
                    projectRepository.findAllByGroupProjectId(groupProjectId).stream()
                            .filter(s -> s.getStatus() != ProjectStatus.ABANDONED)
                            .toList());
        }

        Set<UUID> ownerIds = subProjectsByGroup.values().stream()
                .flatMap(List::stream)
                .map(s -> UUID.fromString(s.getOwnerId().toString()))
                .collect(Collectors.toSet());
        Map<String, User> userById = userRepository.findAllByIds(ownerIds).stream()
                .collect(Collectors.toMap(u -> u.getId().toString(), u -> u, (a, b) -> a));

        Map<UUID, List<ProjectParticipantResponse>> result = new HashMap<>();
        subProjectsByGroup.forEach((groupProjectId, subs) -> result.put(groupProjectId, subs.stream()
                .map(s -> userById.get(s.getOwnerId().toString()))
                .filter(Objects::nonNull)
                .map(ProjectParticipantResponse::fromDomain)
                .toList()));
        return result;
    }
}
