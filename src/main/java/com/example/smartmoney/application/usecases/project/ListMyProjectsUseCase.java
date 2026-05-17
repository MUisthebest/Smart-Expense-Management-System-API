package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.responses.project.ProjectListItemResponse;
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
    public List<ProjectListItemResponse> execute(String actorId) {
        List<Project> projects = projectRepository.findAllByUser(UserId.of(actorId));
        return projects.stream().map(ProjectListItemResponse::fromDomain).toList();
    }
}

