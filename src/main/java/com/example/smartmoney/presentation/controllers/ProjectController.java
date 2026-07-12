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
@Tag(name = "Projects", description = "APIs for managing personal saving projects")
@SecurityRequirement(name = "bearerAuth")
public class ProjectController {

    private final CreateProjectUseCase createProjectUseCase;
    private final UpdateProjectUseCase updateProjectUseCase;
    private final GetProjectDetailUseCase getProjectDetailUseCase;
    private final ListMyProjectsUseCase listMyProjectsUseCase;

    private final DeleteProjectUseCase deleteProjectUseCase;
    private final CreateProjectTrackingUseCase createProjectTrackingUseCase;
    private final GetProjectTrackingUseCase getProjectTrackingUseCase;
    private final UpdateProjectTrackingUseCase updateProjectTrackingUseCase;
    private final DeleteProjectTrackingUseCase deleteProjectTrackingUseCase;
    private final GetProjectHistoryUseCase getProjectHistoryUseCase;
    private final ProjectBudgetAdvisorUseCase projectBudgetAdvisorUseCase;

    private final FreezeProjectUseCase freezeProjectUseCase;
    private final UnfreezeProjectUseCase unfreezeProjectUseCase;

    private final AddProjectContributionUseCase addProjectContributionUseCase;

    private String actorId(Authentication auth) {
        return auth.getName();
    }

    // ================= CREATE =================

    @Operation(
            summary = "Create new project",
            description = "Create a new personal saving project"
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
            description = "Retrieve all projects that current user owns"
    )
    @GetMapping
    public CheckResponse<List<ProjectListItemResponse>> listMine(
            Authentication auth,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProjectType type,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) ProjectPriority priority) {
        ProjectFilterRequest filter = new ProjectFilterRequest(search, type, status, priority);
        List<ProjectListItemResponse> data = listMyProjectsUseCase.execute(actorId(auth), filter);
        return CheckResponse.<List<ProjectListItemResponse>>builder()
                .success(true)
                .message("OK")
                .data(data)
                .build();
    }

    // ================= DETAIL =================

    @Operation(summary = "Get project detail")
    @GetMapping("/{projectId}")
    public CheckResponse<ProjectDetailResponse> detail(
            Authentication auth,
            @Parameter(description = "Project ID (UUID)")
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

    // ================= DELETE =================

    @Operation(summary = "Delete project")
    @DeleteMapping("/{projectId}")
    public CheckResponse<Void> delete(Authentication auth, @PathVariable String projectId) {
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
            @Valid @RequestBody CreateProjectTrackingRequest req) {
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
            @PathVariable String projectId) {
        ProjectTrackingResponse data = getProjectTrackingUseCase.execute(actorId(auth), projectId);
        return CheckResponse.<ProjectTrackingResponse>builder()
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
            @Valid @RequestBody UpdateProjectTrackingRequest req) {
        ProjectTrackingResponse data = updateProjectTrackingUseCase.execute(actorId(auth), projectId, req);
        return CheckResponse.<ProjectTrackingResponse>builder()
                .success(true)
                .message("Updated")
                .data(data)
                .build();
    }

    @Operation(summary = "Delete project tracking")
    @DeleteMapping("/{projectId}/tracking")
    public CheckResponse<Void> deleteTracking(Authentication auth, @PathVariable String projectId) {
        deleteProjectTrackingUseCase.execute(actorId(auth), projectId);
        return CheckResponse.<Void>builder()
                .success(true)
                .message("Deleted")
                .build();
    }

    @Operation(summary = "Get project settlement history snapshots")
    @GetMapping("/{projectId}/history")
    public CheckResponse<List<ProjectHistoryResponse>> getHistory(
            Authentication auth,
            @PathVariable String projectId) {
        List<ProjectHistoryResponse> data = getProjectHistoryUseCase.execute(actorId(auth), projectId);
        return CheckResponse.<List<ProjectHistoryResponse>>builder()
                .success(true)
                .message("OK")
                .data(data)
                .build();
    }

    // ===== AI Budget Advisor =====

    @Operation(
            summary = "Project budget advisor",
            description = "Given project parameters, suggests a monthly saving plan (amount + number of months). " +
                    "The savings pace comes from the user's savingPace trait; this is advisory and never blocks."
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

    // ===== Contributions (press-to-contribute) =====

    @Operation(
            summary = "Contribute to a project",
            description = "Press-to-contribute: records a real EXPENSE ledger transaction and " +
                    "advances the project atomically. Completes the project synchronously if the " +
                    "target is reached. Owner only."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Contribution recorded"),
            @ApiResponse(responseCode = "400", description = "Validation error / project not contributable"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/{projectId}/contributions")
    public CheckResponse<ProjectDetailResponse> contribute(
            Authentication auth,
            @PathVariable String projectId,
            @Valid @RequestBody AddProjectContributionRequest req) {
        ProjectDetailResponse data = addProjectContributionUseCase.execute(actorId(auth), projectId, req);
        return CheckResponse.<ProjectDetailResponse>builder()
                .success(true)
                .message("Contributed")
                .data(data)
                .build();
    }

    // ===== Freeze lifecycle =====

    @Operation(summary = "Freeze project")
    @PostMapping("/{projectId}/freeze")
    public CheckResponse<ProjectDetailResponse> freeze(
            Authentication auth,
            @PathVariable String projectId) {
        ProjectDetailResponse data = freezeProjectUseCase.execute(actorId(auth), projectId);
        return CheckResponse.<ProjectDetailResponse>builder()
                .success(true)
                .message("Frozen")
                .data(data)
                .build();
    }

    @Operation(summary = "Unfreeze project")
    @PostMapping("/{projectId}/unfreeze")
    public CheckResponse<ProjectDetailResponse> unfreeze(
            Authentication auth,
            @PathVariable String projectId) {
        ProjectDetailResponse data = unfreezeProjectUseCase.execute(actorId(auth), projectId);
        return CheckResponse.<ProjectDetailResponse>builder()
                .success(true)
                .message("Unfrozen")
                .data(data)
                .build();
    }
}
