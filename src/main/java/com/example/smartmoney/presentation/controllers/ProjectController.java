package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.requests.project.*;
import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.application.dtos.responses.project.*;
import com.example.smartmoney.application.usecases.project.*;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Slf4j
@Tag(
        name = "Projects",
        description = "APIs for managing saving projects (Personal / Group)"
)
@SecurityRequirement(name = "bearerAuth")
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
    private final DeleteProjectUseCase deleteProjectUseCase;
        private final CreateProjectTrackingUseCase createProjectTrackingUseCase;
        private final GetProjectTrackingUseCase getProjectTrackingUseCase;
        private final UpdateProjectTrackingUseCase updateProjectTrackingUseCase;
        private final DeleteProjectTrackingUseCase deleteProjectTrackingUseCase;
    private final GetProjectHistoryUseCase getProjectHistoryUseCase;
    private final ProjectBudgetAdvisorUseCase projectBudgetAdvisorUseCase;

    private String actorId(Authentication auth) {
        
        return auth.getName();
        //return "1";
    }

    // ================= CREATE =================

    @Operation(
            summary = "Create new project",
            description = "Create a new saving project (Personal or Group)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Project created successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
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

    // ================= LIST =================

    @Operation(
            summary = "Get my projects",
            description = "Retrieve all projects that current user owns or joined"
    )

    @GetMapping
    public CheckResponse<List<ProjectListItemResponse>> listMine(
            Authentication auth,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProjectType type,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) ProjectPriority priority
            ) {
        ProjectFilterRequest filter = new ProjectFilterRequest(search, type, status, priority);

        List<ProjectListItemResponse> data = listMyProjectsUseCase.execute(actorId(auth), filter);
        return CheckResponse.<List<ProjectListItemResponse>>builder()
                .success(true)
                .message("OK")
                .data(data)
                .build();
    }

    // ================= DETAIL =================

    @Operation(
            summary = "Get project detail",
            description = "Retrieve detail of a specific project"
    )
    @GetMapping("/{projectId}")
    public CheckResponse<ProjectDetailResponse> detail(
            Authentication auth,
            @Parameter(description = "Project ID (UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable String projectId) {
        ProjectDetailResponse data = getProjectDetailUseCase.execute(actorId(auth), projectId);
        return CheckResponse.<ProjectDetailResponse>builder()
                .success(true)
                .message("OK")
                .data(data)
                .build();
    }

    // ================= UPDATE =================

    @Operation(summary = "Update project information")
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

    // ================= INVITE MEMBER =================

    @Operation(summary = "Invite member to group project")
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

    @Operation(summary = "Accept project invitation")
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

    @Operation(summary = "Set admin role for member")
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

    @Operation(summary = "Remove member from project")
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

    @Operation(summary = "Add contribution to project")
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

    @Operation(summary = "List contributions of project")
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

    @Operation(summary = "Get contribution summary")
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

    @Operation(summary = "Delete project")
    @DeleteMapping("/{projectId}")
    public CheckResponse<Void> delete(
            Authentication auth,
            @PathVariable String projectId
    ) {
        deleteProjectUseCase.execute(actorId(auth), projectId);

        return CheckResponse.<Void>builder()
                .success(true)
                .message("Deleted")
                .build();
    }

    // ===== Tracking =====

    @Operation(summary = "Create project tracking")
    @PostMapping("/{projectId}/tracking")
    public CheckResponse<ProjectTrackingResponse> createTracking(
            Authentication auth,
            @PathVariable String projectId,
            @Valid @RequestBody CreateProjectTrackingRequest req
    ) {
        ProjectTrackingResponse data = createProjectTrackingUseCase.execute(actorId(auth), projectId, req);
        return CheckResponse.<ProjectTrackingResponse>builder()
                .success(true)
                .message("Created")
                .data(data)
                .build();
    }

    @Operation(summary = "Get project tracking")
    @GetMapping("/{projectId}/tracking")
    public CheckResponse<ProjectTrackingResponse> getTracking(
            Authentication auth,
            @PathVariable String projectId
    ) {
        ProjectTrackingResponse data = getProjectTrackingUseCase.execute(actorId(auth), projectId);
        return CheckResponse.<ProjectTrackingResponse>builder()
                .success(true)
                .message("OK")
                .data(data)
                .build();
    }

    @Operation(summary = "Get project settlement history snapshots")
    @GetMapping("/{projectId}/history")
    public CheckResponse<List<ProjectHistoryResponse>> getHistory(
            Authentication auth,
            @PathVariable String projectId
    ) {
        List<ProjectHistoryResponse> data = getProjectHistoryUseCase.execute(actorId(auth), projectId);
        return CheckResponse.<List<ProjectHistoryResponse>>builder()
                .success(true)
                .message("OK")
                .data(data)
                .build();
    }

    @Operation(summary = "Update project tracking")
    @PutMapping("/{projectId}/tracking")
    public CheckResponse<ProjectTrackingResponse> updateTracking(
            Authentication auth,
            @PathVariable String projectId,
            @Valid @RequestBody UpdateProjectTrackingRequest req
    ) {
        ProjectTrackingResponse data = updateProjectTrackingUseCase.execute(actorId(auth), projectId, req);
        return CheckResponse.<ProjectTrackingResponse>builder()
                .success(true)
                .message("Updated")
                .data(data)
                .build();
    }

    @Operation(summary = "Delete project tracking")
    @DeleteMapping("/{projectId}/tracking")
    public CheckResponse<Void> deleteTracking(
            Authentication auth,
            @PathVariable String projectId
    ) {
        deleteProjectTrackingUseCase.execute(actorId(auth), projectId);
        return CheckResponse.<Void>builder()
                .success(true)
                .message("Deleted")
                .build();
    }

    // ===== AI Budget Advisor =====

    @Operation(
            summary = "AI project budget advisor",
            description = "Given project parameters and a mode (URGENT / RELAXED), computes the adjusted monthly saving and number of months."
    )
    @PostMapping("/project-advisor")
    public CheckResponse<ProjectBudgetAdvisorResponse> projectAdvisor(
            Authentication auth,
            @Valid @RequestBody ProjectBudgetAdvisorRequest req) {
        ProjectBudgetAdvisorResponse data = projectBudgetAdvisorUseCase.execute(actorId(auth), req);
        return CheckResponse.<ProjectBudgetAdvisorResponse>builder()
                .success(true)
                .message("OK")
                .data(data)
                .build();
    }
}
