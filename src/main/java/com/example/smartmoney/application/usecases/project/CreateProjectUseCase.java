package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.requests.project.CreateProjectRequest;
import com.example.smartmoney.application.dtos.responses.project.ProjectDetailResponse;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateProjectUseCase {

    private final ProjectRepository projectRepository;

    @Transactional
    public ProjectDetailResponse execute(String actorId, CreateProjectRequest req) {
        Project p = Project.createNew(
                UserId.of(actorId),
                req.getType(),
                req.getName(),
                req.getDescription(),
                new Money(req.getTargetAmount(), req.getCurrency()),
                req.getDeadline()
        );

        return ProjectDetailResponse.fromDomain(projectRepository.save(p));
    }
}
