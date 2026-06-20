package com.example.smartmoney.application.usecases.project;
import com.example.smartmoney.application.dtos.responses.project.ProjectTrackingResponse;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectTrackingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetUserProjectTrackingUseCase {

    private final ProjectRepository projectRepository;

    private final ProjectTrackingRepository trackingRepository;

    public List<ProjectTrackingResponse> execute(UUID userId) {

        List<Project> projects =
                projectRepository.findAllByUser(
                        UserId.of(userId.toString())
                );

        return projects.stream()
                .map(project -> {

                    ProjectTracking tracking =
                            trackingRepository
                                    .findByProjectId(
                                            project.getId()
                                    )
                                    .orElse(null);

                    return tracking == null
                            ? null
                            : ProjectTrackingResponse.fromDomain(
                                    tracking
                            );

                })
                .filter(Objects::nonNull)
                .toList();
    }
}