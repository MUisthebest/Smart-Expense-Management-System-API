package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.usecases.notification.CreateNotificationUseCase;
import com.example.smartmoney.application.usecases.notification.GetNotificationUseCase;
import com.example.smartmoney.application.usecases.notification.MarkNotificationAsReadUseCase;
import com.example.smartmoney.domain.models.notification.Notification;
import com.example.smartmoney.application.dtos.requests.notification.CreateNotificationRequest;
import com.example.smartmoney.application.dtos.requests.notification.MarkNotificationsReadRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.infrastructure.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;

import java.util.UUID;
import java.util.List;

@Tag(
    name = "Notifications",
    description = "Notification APIs for creating and managing notifications"
)
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final CreateNotificationUseCase createNotificationUseCase;
    private final GetNotificationUseCase getNotificationUseCase;
    private final MarkNotificationAsReadUseCase markNotificationAsReadUseCase;
    private final JwtTokenProvider jwtTokenProvider;

    @Operation(
        summary = "Create a new notification",
        description = "Create a new notification with the provided content"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Notification created successfully",
            content = @Content(schema = @Schema(implementation = CheckResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid input or notification already exists",
            content = @Content(schema = @Schema(implementation = CheckResponse.class))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error"
        )
    })
    @PostMapping
    public ResponseEntity<CheckResponse<Notification>> createNotification(
            @RequestBody CreateNotificationRequest request,
            @RequestHeader("Authorization") String authHeader
    ) {
        UUID userId = extractUserIdFromToken(authHeader);

        Notification notification =
            createNotificationUseCase.createNotification(userId, request.getContent());

        return ResponseEntity.ok(
            CheckResponse.<Notification>builder()
                .success(true)
                .message("Notification created successfully")
                .data(notification)
                .build()
        );
    }

    @PatchMapping("/read")
    public ResponseEntity<CheckResponse<Void>> markManyAsRead(
            @RequestBody MarkNotificationsReadRequest request,
            @RequestHeader("Authorization") String authHeader
    ) {
        UUID userId = extractUserIdFromToken(authHeader);
        markNotificationAsReadUseCase.markManyAsRead(request.getNotificationIds(), userId);
        return ResponseEntity.ok(
            CheckResponse.<Void>builder()
                .success(true)
                .message("Notifications marked as read")
                .build()
        );
    }

    @Operation(
    summary = "Get user notifications",
    description = "Retrieve all notifications of current user (sorted by newest first)"
    )
@ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Notifications retrieved successfully",
        content = @Content(schema = @Schema(implementation = CheckResponse.class))
    ),
    @ApiResponse(
        responseCode = "401",
        description = "Unauthorized"
    ),
    @ApiResponse(
        responseCode = "500",
        description = "Internal server error"
    )
})

    @GetMapping
    public ResponseEntity<CheckResponse<List<Notification>>> getNotifications(
            @Parameter(description = "Bearer token", required = true, hidden = true)
            @RequestHeader("Authorization") String authHeader
    ) {
        UUID userId = extractUserIdFromToken(authHeader);

        List<Notification> notifications = getNotificationUseCase.getByUserId(userId);

        CheckResponse<List<Notification>> response = CheckResponse.<List<Notification>>builder()
                .success(true)
                .message("Notifications retrieved successfully")
                .data(notifications)
                .build();

        return ResponseEntity.ok(response);
    }

        /**
     * Extract user ID from JWT token in Authorization header
     */
    private UUID extractUserIdFromToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return jwtTokenProvider.getUserIdFromToken(token);
        }
        throw new IllegalArgumentException("Invalid Authorization header");
    }
}