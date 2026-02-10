package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.requests.project.*;
import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.application.dtos.responses.project.*;
import com.example.smartmoney.application.usecases.project.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Slf4j
@Tag(
        name = "Project",
        description = "Project"
)
public class ProjectController {

    private final CreateProjectUseCase createProjectUseCase;
    private final UpdateProjectUseCase updateProjectUseCase;
    private final GetProjectDetailUseCase getProjectDetailUseCase;
    private final ListMyProjectsUseCase listMyProjectsUseCase;

    private final InviteProjectMemberUseCase inviteProjectMemberUseCase;
    private final AcceptProjectInviteUseCase acceptProjectInviteUseCase;
    private final SetProjectAdminUseCase setProjectAdminUseCase;
    private final RemoveProjectMemberUseCase removeProjectMemberUseCase;

    private final AddProjectContributionUseCase addProjectContributionUseCase;
    private final ListProjectContributionsUseCase listProjectContributionsUseCase;
    private final GetProjectContributionSummaryUseCase getProjectContributionSummaryUseCase;

    private String actorId(Authentication auth) {
        
        //return auth.getName();
        return "1";
    }

    @PostMapping
    public CheckResponse<ProjectDetailResponse> create(Authentication auth,
                                                       @Valid @RequestBody CreateProjectRequest req) {
        ProjectDetailResponse data = createProjectUseCase.execute(actorId(auth), req);
        return CheckResponse.<ProjectDetailResponse>builder()
                .success(true)
                .message("Created")
                .data(data)
                .build();
    }

    @GetMapping
    public CheckResponse<List<ProjectListItemResponse>> listMine(Authentication auth) {
        List<ProjectListItemResponse> data = listMyProjectsUseCase.execute(actorId(auth));
        return CheckResponse.<List<ProjectListItemResponse>>builder()
                .success(true)
                .message("OK")
                .data(data)
                .build();
    }

    @GetMapping("/{projectId}")
    public CheckResponse<ProjectDetailResponse> detail(Authentication auth, @PathVariable String projectId) {
        ProjectDetailResponse data = getProjectDetailUseCase.execute(actorId(auth), projectId);
        return CheckResponse.<ProjectDetailResponse>builder()
                .success(true)
                .message("OK")
                .data(data)
                .build();
    }

    @PutMapping("/{projectId}")
    public CheckResponse<ProjectDetailResponse> update(Authentication auth,
                                                       @PathVariable String projectId,
                                                       @Valid @RequestBody UpdateProjectRequest req) {
        ProjectDetailResponse data = updateProjectUseCase.execute(actorId(auth), projectId, req);
        return CheckResponse.<ProjectDetailResponse>builder()
                .success(true)
                .message("Updated")
                .data(data)
                .build();
    }

    // ===== Members (GROUP) =====
    @PostMapping("/{projectId}/members/invite")
    public CheckResponse<ProjectDetailResponse> invite(Authentication auth,
                                                       @PathVariable String projectId,
                                                       @Valid @RequestBody InviteProjectMemberRequest req) {
        ProjectDetailResponse data = inviteProjectMemberUseCase.execute(actorId(auth), projectId, req);
        return CheckResponse.<ProjectDetailResponse>builder()
                .success(true)
                .message("Invited")
                .data(data)
                .build();
    }

    @PostMapping("/{projectId}/members/accept")
    public CheckResponse<ProjectDetailResponse> accept(Authentication auth,
                                                       @PathVariable String projectId) {
        ProjectDetailResponse data = acceptProjectInviteUseCase.execute(actorId(auth), projectId);
        return CheckResponse.<ProjectDetailResponse>builder()
                .success(true)
                .message("Accepted")
                .data(data)
                .build();
    }

    @PatchMapping("/{projectId}/members/{userId}/admin")
    public CheckResponse<ProjectDetailResponse> setAdmin(Authentication auth,
                                                         @PathVariable String projectId,
                                                         @PathVariable String userId,
                                                         @Valid @RequestBody SetProjectAdminRequest req) {
        ProjectDetailResponse data = setProjectAdminUseCase.execute(actorId(auth), projectId, userId, req);
        return CheckResponse.<ProjectDetailResponse>builder()
                .success(true)
                .message("Updated")
                .data(data)
                .build();
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    public CheckResponse<ProjectDetailResponse> removeMember(Authentication auth,
                                                             @PathVariable String projectId,
                                                             @PathVariable String userId) {
        ProjectDetailResponse data = removeProjectMemberUseCase.execute(actorId(auth), projectId, userId);
        return CheckResponse.<ProjectDetailResponse>builder()
                .success(true)
                .message("Removed")
                .data(data)
                .build();
    }

    // ===== Contributions =====
    @PostMapping("/{projectId}/contributions")
    public CheckResponse<ProjectContributionResponse> addContribution(Authentication auth,
                                                                      @PathVariable String projectId,
                                                                      @Valid @RequestBody AddProjectContributionRequest req) {
        ProjectContributionResponse data = addProjectContributionUseCase.execute(actorId(auth), projectId, req);
        return CheckResponse.<ProjectContributionResponse>builder()
                .success(true)
                .message("Added")
                .data(data)
                .build();
    }

    @GetMapping("/{projectId}/contributions")
    public CheckResponse<List<ProjectContributionResponse>> listContributions(Authentication auth,
                                                                              @PathVariable String projectId) {
        List<ProjectContributionResponse> data = listProjectContributionsUseCase.execute(actorId(auth), projectId);
        return CheckResponse.<List<ProjectContributionResponse>>builder()
                .success(true)
                .message("OK")
                .data(data)
                .build();
    }

    @GetMapping("/{projectId}/contributions/summary")
    public CheckResponse<ContributionSummaryResponse> summary(Authentication auth,
                                                              @PathVariable String projectId) {
        ContributionSummaryResponse data = getProjectContributionSummaryUseCase.execute(actorId(auth), projectId);
        return CheckResponse.<ContributionSummaryResponse>builder()
                .success(true)
                .message("OK")
                .data(data)
                .build();
    }
}
