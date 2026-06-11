package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.dtos.requests.group.InviteGroupMemberRequest;
import com.example.smartmoney.application.dtos.responses.group.GroupInviteResponse;
import com.example.smartmoney.application.ports.invite.GroupInviteTokenPort;
import com.example.smartmoney.application.ports.mail.MailPort;
import com.example.smartmoney.application.ports.notification.PushNotificationSenderPort;
import com.example.smartmoney.application.ports.notification.PushTokenPort;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.valueobjects.Email;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.repositories.GroupRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InviteGroupMemberUseCase {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupInviteTokenPort inviteTokenPort;
    private final MailPort mailPort;
    private final PushTokenPort pushTokenPort;
    private final PushNotificationSenderPort pushSender;

    @Value("${app.base-url:https://yourapp.com}")
    private String baseUrl;

    @Transactional
    public GroupInviteResponse execute(String actorId, String groupId, InviteGroupMemberRequest req) {
        Group group = groupRepository.findById(GroupId.of(groupId))
                .orElseThrow(() -> new ProjectException("Group not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        if (!UserId.of(actorId).equals(group.getAdminId())) {
            throw new ProjectException("Only group admin can invite members",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }

        User invitedUser = userRepository.findByEmail(new Email(req.getEmail()))
                .orElseThrow(() -> new DomainException("User with email " + req.getEmail() + " not found"));

        group.inviteMember(UserId.of(invitedUser.getId().toString()));
        groupRepository.save(group);

        String token = UUID.randomUUID().toString().replace("-", "");
        inviteTokenPort.save(token, UUID.fromString(groupId));

        String deepLink = baseUrl + "/api/v1/groups/invites/accept-direct?token=" + token;

        try {
            dispatchNotifications(invitedUser.getId(), group.getName(), deepLink, token);
        } catch (Exception e) {
            log.warn("[GroupInvite] Notification failed for {} on group {}: {}", req.getEmail(), groupId, e.getMessage());
        }

        return GroupInviteResponse.builder()
                .token(token)
                .deepLinkUrl(deepLink)
                .message("Invitation sent. Link expires in 7 days.")
                .build();
    }

    private void dispatchNotifications(UUID invitedUserId, String groupName, String deepLink, String token) {
        var pushTokens = pushTokenPort.findByUserId(invitedUserId.toString());
        if (!pushTokens.isEmpty()) {
            String body = "You've been invited to join group \"" + groupName + "\". Tap to accept.";
            Map<String, String> data = Map.of("path", "/group-invite/" + token);
            for (var pt : pushTokens) {
                pushSender.send(pt.getPushToken(), "Group Invitation", body, data);
            }
        }
        userRepository.findById(invitedUserId).ifPresent(user -> {
            String email = user.getEmail() != null ? user.getEmail().getValue() : null;
            if (email != null && !email.isBlank()) {
                String html = buildEmailHtml(user.getFullName(), groupName, deepLink);
                mailPort.sendHtmlMail(email, "You're invited to join group " + groupName, html);
            }
        });
    }

    private String buildEmailHtml(String name, String groupName, String deepLink) {
        String displayName = (name != null && !name.isBlank()) ? name : "there";
        return """
                <!DOCTYPE html><html><body style="font-family:Arial,sans-serif;background:#f5f5f5;padding:24px">
                  <div style="max-width:520px;margin:auto;background:#fff;border-radius:12px;padding:32px">
                    <h2 style="color:#1a1a2e">Group Invitation 🎉</h2>
                    <p>Hi <strong>%s</strong>,</p>
                    <p>You have been invited to join the savings group <strong>"%s"</strong>.</p>
                    <a href="%s" style="display:inline-block;background:#6c63ff;color:#fff;padding:14px 28px;
                       border-radius:8px;text-decoration:none;font-weight:bold;margin-top:8px">Accept Invitation</a>
                    <p style="margin-top:24px;color:#888;font-size:12px">This link expires in 7 days.</p>
                  </div>
                </body></html>
                """.formatted(displayName, groupName, deepLink);
    }
}
