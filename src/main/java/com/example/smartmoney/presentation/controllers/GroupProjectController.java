package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.requests.group.*;
import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.application.dtos.responses.group.*;
import com.example.smartmoney.application.dtos.responses.project.ProjectDetailResponse;
import com.example.smartmoney.application.usecases.group.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/group-projects")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Group Projects", description = "APIs for group project creation, progress tracking, and sub-personal project joining")
@SecurityRequirement(name = "bearerAuth")
public class GroupProjectController {

    private final GetGroupProjectSuggestionsUseCase getGroupProjectSuggestionsUseCase;
    private final CreateGroupProjectUseCase createGroupProjectUseCase;
    private final GetGroupProjectDetailUseCase getGroupProjectDetailUseCase;
    private final CreateSubPersonalProjectUseCase createSubPersonalProjectUseCase;
    private final DissolveGroupProjectUseCase dissolveGroupProjectUseCase;
    private final RespondToSponsorshipRequestUseCase respondToSponsorshipRequestUseCase;
    private final GetPendingSponsorshipRequestsUseCase getPendingSponsorshipRequestsUseCase;

    private String actorId(Authentication auth) {
        return auth.getName();
    }

    @Operation(
            summary = "Get group project suggestions",
            description = "Returns two capacity-based suggestions given either an input amount or input months. Requires the group to be LOCKED."
    )
    @PostMapping("/suggestions")
    public CheckResponse<GroupProjectSuggestionsResponse> suggestions(
            Authentication auth,
            @Valid @RequestBody GetGroupProjectSuggestionsRequest req) {
        GroupProjectSuggestionsResponse data = getGroupProjectSuggestionsUseCase.execute(actorId(auth), req);
        return CheckResponse.<GroupProjectSuggestionsResponse>builder()
                .success(true)
                .message("OK")
                .data(data)
                .build();
    }

    @Operation(
            summary = "Create group project",
            description = "Admin creates a group project with a fixed target amount and duration. Requires LOCKED group and must be done on days 1-7 of the month."
    )
    @PostMapping
    public CheckResponse<GroupProjectDetailResponse> create(
            Authentication auth,
            @Valid @RequestBody CreateGroupProjectRequest req) {
        GroupProjectDetailResponse data = createGroupProjectUseCase.execute(actorId(auth), req);
        return CheckResponse.<GroupProjectDetailResponse>builder()
                .success(true)
                .message("Created")
                .data(data)
                .build();
    }

    @Operation(summary = "Get group project detail", description = "Returns aggregate progress and per-member sub-project status. Auto-marks COMPLETED if aggregate money saved >= target.")
    @GetMapping("/{groupProjectId}")
    public CheckResponse<GroupProjectDetailResponse> detail(
            Authentication auth,
            @PathVariable String groupProjectId) {
        GroupProjectDetailResponse data = getGroupProjectDetailUseCase.execute(actorId(auth), groupProjectId);
        return CheckResponse.<GroupProjectDetailResponse>builder()
                .success(true)
                .message("OK")
                .data(data)
                .build();
    }

    @Operation(
            summary = "Join group project (create sub-personal project)",
            description = "A JOINED group member picks a priority and creates their personal sub-project linked to this group project. Exempt from the day 1-7 creation window."
    )
    @PostMapping("/{groupProjectId}/join")
    public CheckResponse<ProjectDetailResponse> join(
            Authentication auth,
            @PathVariable String groupProjectId,
            @Valid @RequestBody CreateSubPersonalProjectRequest req) {
        ProjectDetailResponse data = createSubPersonalProjectUseCase.execute(actorId(auth), groupProjectId, req);
        return CheckResponse.<ProjectDetailResponse>builder()
                .success(true)
                .message("Joined")
                .data(data)
                .build();
    }

    @Operation(
            summary = "Dissolve group project",
            description = "Admin dissolves the group project. All ACTIVE/FROZEN sub-personal projects are abandoned and the group is dissolved."
    )
    @PostMapping("/{groupProjectId}/dissolve")
    public CheckResponse<Void> dissolve(
            Authentication auth,
            @PathVariable String groupProjectId) {
        dissolveGroupProjectUseCase.execute(actorId(auth), groupProjectId);
        return CheckResponse.<Void>builder()
                .success(true)
                .message("Dissolved")
                .build();
    }

    @Operation(summary = "Respond to sponsorship request")
    @PostMapping("/sponsorship/{requestId}/respond")
    public CheckResponse<Void> respond(
            Authentication auth,
            @PathVariable String requestId,
            @Valid @RequestBody RespondToSponsorshipRequest req) {
        respondToSponsorshipRequestUseCase.execute(actorId(auth), requestId, req.getAgreed());
        return CheckResponse.<Void>builder()
                .success(true)
                .message("Responded successfully")
                .build();
    }

    @Operation(summary = "Get pending sponsorship requests for current user")
    @GetMapping("/sponsorship/pending")
    public CheckResponse<List<GroupProjectSponsorshipRequestResponse>> getPending(Authentication auth) {
        List<GroupProjectSponsorshipRequestResponse> data = getPendingSponsorshipRequestsUseCase.execute(actorId(auth));
        return CheckResponse.<List<GroupProjectSponsorshipRequestResponse>>builder()
                .success(true)
                .message("OK")
                .data(data)
                .build();
    }
}
