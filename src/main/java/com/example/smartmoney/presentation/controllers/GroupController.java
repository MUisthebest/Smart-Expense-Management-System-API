package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.requests.group.*;
import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.application.dtos.responses.group.*;
import com.example.smartmoney.application.usecases.group.*;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Groups", description = "APIs for group formation and member management")
@SecurityRequirement(name = "bearerAuth")
public class GroupController {

    private final ListMyGroupsUseCase listMyGroupsUseCase;
    private final CreateGroupUseCase createGroupUseCase;
    private final GetGroupDetailUseCase getGroupDetailUseCase;
    private final LockGroupUseCase lockGroupUseCase;
    private final UnlockGroupUseCase unlockGroupUseCase;
    private final InviteGroupMemberUseCase inviteGroupMemberUseCase;
    private final AcceptGroupInviteUseCase acceptGroupInviteUseCase;
    private final DeclineGroupInviteUseCase declineGroupInviteUseCase;
    private final RemoveGroupMemberUseCase removeGroupMemberUseCase;
    private final DeleteGroupUseCase deleteGroupUseCase;

    private String actorId(Authentication auth) {
        return auth.getName();
    }

    @Operation(summary = "List my groups", description = "Returns all non-dissolved groups where the current user is a JOINED member or admin. Sorted by creation date descending.")
    @GetMapping
    public CheckResponse<List<GroupSummaryResponse>> listMine(Authentication auth) {
        List<GroupSummaryResponse> data = listMyGroupsUseCase.execute(actorId(auth));
        return CheckResponse.<List<GroupSummaryResponse>>builder()
                .success(true)
                .message("OK")
                .data(data)
                .build();
    }

    @Operation(summary = "Create a new group", description = "Admin creates a group; admin is auto-joined with their capacity snapshot.")
    @PostMapping
    public CheckResponse<GroupDetailResponse> create(
            Authentication auth,
            @Valid @RequestBody CreateGroupRequest req) {
        GroupDetailResponse data = createGroupUseCase.execute(actorId(auth), req);
        return CheckResponse.<GroupDetailResponse>builder()
                .success(true)
                .message("Created")
                .data(data)
                .build();
    }

    @Operation(summary = "Get group detail")
    @GetMapping("/{groupId}")
    public CheckResponse<GroupDetailResponse> detail(
            Authentication auth,
            @PathVariable String groupId) {
        GroupDetailResponse data = getGroupDetailUseCase.execute(actorId(auth), groupId);
        return CheckResponse.<GroupDetailResponse>builder()
                .success(true)
                .message("OK")
                .data(data)
                .build();
    }

    @Operation(summary = "Lock the group", description = "Admin explicitly locks the group when satisfied with the member lineup. At least one non-admin member must be JOINED.")
    @PostMapping("/{groupId}/lock")
    public CheckResponse<GroupDetailResponse> lock(
            Authentication auth,
            @PathVariable String groupId) {
        GroupDetailResponse data = lockGroupUseCase.execute(actorId(auth), groupId);
        return CheckResponse.<GroupDetailResponse>builder()
                .success(true)
                .message("Locked")
                .data(data)
                .build();
    }

    @Operation(summary = "Unlock the group", description = "Admin unlocks the group to add more members. Only allowed if no group project exists yet.")
    @PostMapping("/{groupId}/unlock")
    public CheckResponse<GroupDetailResponse> unlock(
            Authentication auth,
            @PathVariable String groupId) {
        GroupDetailResponse data = unlockGroupUseCase.execute(actorId(auth), groupId);
        return CheckResponse.<GroupDetailResponse>builder()
                .success(true)
                .message("Unlocked")
                .data(data)
                .build();
    }

    @Operation(summary = "Invite a member to the group", description = "Admin invites a user by email. A one-time invite token (7-day TTL) is generated and sent via push + email.")
    @PostMapping("/{groupId}/members/invite")
    public CheckResponse<GroupInviteResponse> invite(
            Authentication auth,
            @PathVariable String groupId,
            @Valid @RequestBody InviteGroupMemberRequest req) {
        GroupInviteResponse data = inviteGroupMemberUseCase.execute(actorId(auth), groupId, req);
        return CheckResponse.<GroupInviteResponse>builder()
                .success(true)
                .message("Invited")
                .data(data)
                .build();
    }

    @Operation(summary = "Accept a group invite", description = "Redeems the one-time invite token. Gates: capacity > 0, active projects < 3, currency match. Burns token on success.")
    @PostMapping("/invites/accept")
    public CheckResponse<GroupDetailResponse> accept(
            Authentication auth,
            @Valid @RequestBody AcceptGroupInviteRequest req) {
        GroupDetailResponse data = acceptGroupInviteUseCase.execute(actorId(auth), req);
        return CheckResponse.<GroupDetailResponse>builder()
                .success(true)
                .message("Accepted")
                .data(data)
                .build();
    }

    @Operation(summary = "Decline a group invite", description = "Redeems the token and marks the invite as DECLINED. Burns token on success.")
    @PostMapping("/invites/decline")
    public CheckResponse<GroupDetailResponse> decline(
            Authentication auth,
            @Valid @RequestBody DeclineGroupInviteRequest req) {
        GroupDetailResponse data = declineGroupInviteUseCase.execute(actorId(auth), req);
        return CheckResponse.<GroupDetailResponse>builder()
                .success(true)
                .message("Declined")
                .data(data)
                .build();
    }

    @Operation(summary = "Remove a declined member", description = "Admin removes a DECLINED member entry. May trigger group auto-lock if all remaining invites are resolved.")
    @DeleteMapping("/{groupId}/members/{userId}")
    public CheckResponse<GroupDetailResponse> removeMember(
            Authentication auth,
            @PathVariable String groupId,
            @PathVariable String userId) {
        GroupDetailResponse data = removeGroupMemberUseCase.execute(actorId(auth), groupId, userId);
        return CheckResponse.<GroupDetailResponse>builder()
                .success(true)
                .message("Removed")
                .data(data)
                .build();
    }

    @Operation(summary = "Delete group", description = "Admin deletes the group if no group project has been created yet.")
    @DeleteMapping("/{groupId}")
    public CheckResponse<Void> delete(
            Authentication auth,
            @PathVariable String groupId) {
        deleteGroupUseCase.execute(actorId(auth), groupId);
        return CheckResponse.<Void>builder()
                .success(true)
                .message("Group deleted successfully")
                .build();
    }
}
