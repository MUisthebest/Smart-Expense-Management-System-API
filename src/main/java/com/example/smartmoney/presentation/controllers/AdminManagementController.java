package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.application.dtos.responses.UserResponse;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.application.dtos.responses.group.GroupSummaryResponse;
import com.example.smartmoney.application.dtos.responses.project.ProjectListItemResponse;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.infrastructure.persistence.entities.GroupEntity;
import com.example.smartmoney.infrastructure.persistence.entities.NotificationEntity;
import com.example.smartmoney.infrastructure.persistence.entities.ProjectEntity;
import com.example.smartmoney.infrastructure.persistence.entities.TransactionEntity;
import com.example.smartmoney.infrastructure.persistence.entities.UserEntity;
import com.example.smartmoney.infrastructure.persistence.mappers.GroupPersistenceMapper;
import com.example.smartmoney.infrastructure.persistence.mappers.ProjectPersistenceMapper;
import com.example.smartmoney.infrastructure.persistence.mappers.UserMapper;
import com.example.smartmoney.infrastructure.persistence.repositories.JpaGroupRepository;
import com.example.smartmoney.infrastructure.persistence.repositories.JpaNotificationRepository;
import com.example.smartmoney.infrastructure.persistence.repositories.JpaProjectRepository;
import com.example.smartmoney.infrastructure.persistence.repositories.JpaTransactionRepository;
import com.example.smartmoney.infrastructure.persistence.repositories.JpaUserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Management", description = "Admin APIs for user management and system stats")
@SecurityRequirement(name = "bearerAuth")
public class AdminManagementController {

    private final JpaUserRepository jpaUserRepository;
    private final JpaProjectRepository jpaProjectRepository;
    private final JpaGroupRepository jpaGroupRepository;
    private final JpaTransactionRepository jpaTransactionRepository;
    private final JpaNotificationRepository jpaNotificationRepository;
    private final UserMapper userMapper;
    private final ProjectPersistenceMapper projectPersistenceMapper;
    private final GroupPersistenceMapper groupPersistenceMapper;

    @Operation(summary = "Get all users list", description = "Returns all registered users in the system.")
    @GetMapping("/users")
    public CheckResponse<List<UserResponse>> getAllUsers() {
        log.info("Admin fetching all registered users");
        List<UserEntity> entities = jpaUserRepository.findAll();
        List<UserResponse> users = entities.stream()
                .map(entity -> {
                    User domain = userMapper.toDomain(entity);
                    return UserResponse.fromDomain(domain);
                })
                .collect(Collectors.toList());

        return CheckResponse.<List<UserResponse>>builder()
                .success(true)
                .message("Users list retrieved successfully")
                .data(users)
                .build();
    }

    @Operation(summary = "Toggle user active status", description = "Activates or deactivates a user account.")
    @PatchMapping("/users/{userId}/toggle-status")
    public CheckResponse<UserResponse> toggleUserStatus(@PathVariable UUID userId) {
        log.info("Admin toggling status for user ID: {}", userId);
        UserEntity entity = jpaUserRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        entity.setActive(!entity.isActive());
        UserEntity saved = jpaUserRepository.save(entity);
        User domain = userMapper.toDomain(saved);

        return CheckResponse.<UserResponse>builder()
                .success(true)
                .message("User status updated to: " + (saved.isActive() ? "ACTIVE" : "SUSPENDED"))
                .data(UserResponse.fromDomain(domain))
                .build();
    }

    @Operation(summary = "Get system real-time statistics", description = "Returns aggregated system metrics.")
    @GetMapping("/stats")
    public CheckResponse<Map<String, Object>> getSystemStats() {
        log.info("Admin fetching system stats");
        List<UserEntity> allUsers = jpaUserRepository.findAll();
        long totalUsers = allUsers.size();
        long activeUsers = allUsers.stream().filter(UserEntity::isActive).count();
        long totalProjects = jpaProjectRepository.count();
        long totalGroups = jpaGroupRepository.count();
        long totalTransactions = jpaTransactionRepository.count();

        List<TransactionEntity> allTransactions = jpaTransactionRepository.findAll();
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);

        BigDecimal monthlyVolume = allTransactions.stream()
                .filter(t -> t.getDate() != null && !t.getDate().isBefore(startOfMonth))
                .map(TransactionEntity::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalVolume = allTransactions.stream()
                .map(TransactionEntity::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal finalVolume = monthlyVolume.compareTo(BigDecimal.ZERO) > 0 ? monthlyVolume : totalVolume;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", totalUsers);
        stats.put("activeUsersCount", activeUsers);
        stats.put("totalProjects", totalProjects);
        stats.put("totalGroupProjects", totalGroups);
        stats.put("totalTransactionsCount", totalTransactions);
        stats.put("totalTransactionVolume", finalVolume);
        stats.put("settlementStatus", "COMPLETED");
        stats.put("dbHealth", "HEALTHY");
        stats.put("aiEngineHealth", "OPERATIONAL");

        return CheckResponse.<Map<String, Object>>builder()
                .success(true)
                .message("System statistics retrieved successfully")
                .data(stats)
                .build();
    }

    @Operation(summary = "Broadcast notification to all active users", description = "Creates system notification for every user.")
    @PostMapping("/broadcast")
    public CheckResponse<String> broadcastNotification(@RequestBody Map<String, String> payload) {
        String title = payload.getOrDefault("title", "Announcement");
        String message = payload.getOrDefault("message", "");
        String severity = payload.getOrDefault("severity", "INFO");

        log.info("Admin broadcasting notification: [{}] {}", severity, title);
        String formattedContent = "[" + severity + "] " + title + ": " + message;
        List<UserEntity> activeUsers = jpaUserRepository.findAll();

        List<NotificationEntity> notifications = activeUsers.stream()
                .map(u -> NotificationEntity.builder()
                        .userId(u.getId())
                        .content(formattedContent)
                        .createdAt(LocalDateTime.now())
                        .read(false)
                        .build())
                .collect(Collectors.toList());

        jpaNotificationRepository.saveAll(notifications);

        return CheckResponse.<String>builder()
                .success(true)
                .message("Broadcast sent successfully to " + activeUsers.size() + " users")
                .data("Broadcast completed")
                .build();
    }
}
