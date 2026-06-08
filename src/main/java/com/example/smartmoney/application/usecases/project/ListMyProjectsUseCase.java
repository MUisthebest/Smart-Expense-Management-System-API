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

    @Transactional(readOnly = true)
    public List<ProjectListItemResponse> execute(String actorId, ProjectFilterRequest filter) {
        List<Project> projects =
                projectRepository.findAllByUser(UserId.of(actorId), filter);

        if (projects.isEmpty()) {
            throw new ProjectException(
                    "No projects found matching the given filters",
                    ProjectErrorCode.PROJECT_NOT_FOUND
            );
        }


        return projects.stream()
                .map(ProjectListItemResponse::fromDomain)
                .toList();
    }
}

