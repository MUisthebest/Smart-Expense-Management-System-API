package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.requests.project.InviteProjectMemberRequest;
import com.example.smartmoney.application.dtos.responses.project.InviteResponse;
import com.example.smartmoney.application.ports.invite.InviteTokenPort;
import com.example.smartmoney.application.ports.mail.MailPort;
import com.example.smartmoney.application.ports.notification.PushNotificationSenderPort;
import com.example.smartmoney.application.ports.notification.PushTokenPort;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.user.valueobjects.Email;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Invites a user to a GROUP project via a tokenized deep-link strategy.
 *
 * <ol>
 *   <li>Validates the project and the admin actor.</li>
 *   <li>Records a {@code JoinStatus.INVITED} entry in the {@code project_members} table.</li>
 *   <li>Generates a secure UUID token stored in Redis (7-day TTL).</li>
 *   <li>Dispatches a push notification (if the invitee has a registered push token)
 *       and an HTML email, both containing the deep-link URL.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InviteProjectMemberUseCase {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final InviteTokenPort inviteTokenPort;
    private final MailPort mailPort;
    private final PushTokenPort pushTokenPort;
    private final PushNotificationSenderPort pushSender;

    @Value("${app.base-url:https://yourapp.com}")
    private String baseUrl;

    @Transactional
    public InviteResponse execute(String actorId, String projectId, InviteProjectMemberRequest req) {

        Email email = new Email(req.getEmail());
        User invitedUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new DomainException("User with email " + req.getEmail() + " not found"));

        UUID invitedUserUuid = invitedUser.getId();

        Project project = projectRepository.findById(ProjectId.of(projectId))
                .orElseThrow(() -> new ProjectException("Project not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        // Let the domain model enforce GROUP-only + admin guard + duplicate check
        project.inviteMember(UserId.of(actorId), UserId.of(invitedUserUuid.toString()), req.isAdmin());
        projectRepository.save(project);

        // Generate a one-time invite token stored in Redis (7-day TTL)
        String token = UUID.randomUUID().toString().replace("-", "");
        inviteTokenPort.save(token, UUID.fromString(projectId), req.isAdmin());

        String deepLink = baseUrl + "/api/v1/projects/invites/accept-direct?token=" + token;

        // Dispatch notifications (best-effort – failures must not roll back the invite)
        try {
            dispatchNotifications(invitedUserUuid, project.getName(), deepLink, token);
        } catch (Exception e) {
            log.warn("[Invite] Notification dispatch failed for user {} on project {}: {}",
                    req.getEmail(), projectId, e.getMessage());
        }

        log.info("[Invite] Token {} generated for user {} on project {}", token, req.getEmail(), projectId);

        return InviteResponse.builder()
                .token(token)
                .deepLinkUrl(deepLink)
                .message("Invitation sent. Link expires in 7 days.")
                .build();
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private void dispatchNotifications(UUID invitedUserId, String projectName,
                                       String deepLink, String token) {

        // 1. Push notification (if the user has a registered Expo token)
        var pushTokens = pushTokenPort.findByUserId(invitedUserId.toString());
        if (!pushTokens.isEmpty()) {
            String pushBody = "You've been invited to \"" + projectName + "\". Tap to join.";
            Map<String, String> data = Map.of("path", "/invite/" + token);
            for (var pt : pushTokens) {
                pushSender.send(pt.getPushToken(), "Project Invitation", pushBody, data);
            }
        }

        // 2. HTML email (if we can resolve the invitee's email address)
        userRepository.findById(invitedUserId).ifPresent(user -> {
            String email = user.getEmail() != null ? user.getEmail().getValue() : null;
            if (email != null && !email.isBlank()) {
                String html = buildInviteEmailHtml(user.getFullName(), projectName, deepLink);
                mailPort.sendHtmlMail(email, "You're invited to join " + projectName, html);
            }
        });
    }

    private String buildInviteEmailHtml(String recipientName, String projectName, String deepLink) {
        String displayName = (recipientName != null && !recipientName.isBlank()) ? recipientName : "there";
        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family:Arial,sans-serif;background:#f5f5f5;padding:24px">
                  <div style="max-width:520px;margin:auto;background:#fff;border-radius:12px;padding:32px">
                    <h2 style="color:#1a1a2e">You've been invited! 🎉</h2>
                    <p>Hi <strong>%s</strong>,</p>
                    <p>You have been invited to join the saving project <strong>"%s"</strong>.</p>
                    <p>Click the button below to accept the invitation:</p>
                    <a href="%s"
                       style="display:inline-block;background:#6c63ff;color:#fff;padding:14px 28px;
                              border-radius:8px;text-decoration:none;font-weight:bold;margin-top:8px">
                      Join Project
                    </a>
                    <p style="margin-top:24px;color:#888;font-size:12px">
                      This link expires in 7 days. If you weren't expecting this invitation, you can ignore this email.
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(displayName, projectName, deepLink);
    }
}
