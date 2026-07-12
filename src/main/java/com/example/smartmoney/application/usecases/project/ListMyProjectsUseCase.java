package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.requests.project.ProjectFilterRequest;
import com.example.smartmoney.application.dtos.responses.project.ProjectListItemResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListMyProjectsUseCase {

    private final ProjectRepository projectRepository;

    @Transactional
    public List<ProjectListItemResponse> execute(String actorId, ProjectFilterRequest filter) {
        List<Project> projects =
                projectRepository.findAllByUser(UserId.of(actorId), filter);

        if (projects.isEmpty()) {
            throw new ProjectException(
                    "No projects found matching the given filters",
                    ProjectErrorCode.PROJECT_NOT_FOUND
            );
        }

        // Lazy-on-read expiry (replaces the settlement cron's expiry pass). Persist any
        // project that just crossed its deadline under target; the in-memory status is
        // already updated, so the mapped response reflects EXPIRED either way.
        projects.forEach(p -> {
            if (p.expireIfDeadlinePassed()) {
                projectRepository.save(p);
            }
        });

        return projects.stream()
                .map(ProjectListItemResponse::fromDomain)
                .toList();
    }
}

