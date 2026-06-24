package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.ports.notification.PushNotificationSenderPort;
import com.example.smartmoney.application.ports.notification.PushTokenPort;
import com.example.smartmoney.application.usecases.notification.CreateNotificationUseCase;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.GroupMember;
import com.example.smartmoney.domain.models.group.GroupProject;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.repositories.GroupRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Fan-out notifications for group-project lifecycle events (completion, a member dropping out,
 * and group expiry). Each joined member (including the admin) gets a persistent in-app notification
 * carrying a deep link to the group project and, if they have push tokens, a push. Per-member
 * failures are logged and never propagate, so a single bad token can't abort the flow or roll back
 * the caller's transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupProjectNotificationUseCase {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final CreateNotificationUseCase createNotificationUseCase;
    private final PushTokenPort pushTokenPort;
    private final PushNotificationSenderPort pushSender;

    public void notifyCompleted(GroupProject gp) {
        broadcast(gp, "Group project completed! 🎉",
                "\"" + gp.getName() + "\" reached its goal. Congratulations!");
    }

    /**
     * A member's sub-project expired/was abandoned, so they dropped out and the group's required
     * target shrank to the remaining participants. Notifies everyone still in the group.
     */
    public void notifyMemberDropped(GroupProject gp, UUID droppedUserId) {
        String who = resolveUsername(droppedUserId);
        broadcast(gp, "A member dropped out",
                who + " can no longer contribute to \"" + gp.getName()
                        + "\". The group goal was reduced to the remaining members.");
    }

    /** No participating members remain — the group goal can no longer be met. */
    public void notifyGroupExpired(GroupProject gp) {
        broadcast(gp, "Group project expired",
                "\"" + gp.getName() + "\" expired because no members are still contributing.");
    }

    private void broadcast(GroupProject gp, String title, String body) {
        Group group = groupRepository.findById(GroupId.of(gp.getGroupId().toString())).orElse(null);
        if (group == null) {
            log.warn("[GroupProject] Group project {} has no group {} — skipping notifications.",
                    gp.getId().value(), gp.getGroupId());
            return;
        }

        String deepLink = "/group-projects/" + gp.getId().value();
        for (GroupMember member : group.joinedMembers()) {
            try {
                createNotificationUseCase.createNotification(member.getUserId().value(), body, deepLink);

                var tokens = pushTokenPort.findByUserId(member.getUserId().toString());
                Map<String, String> data = Map.of("url", deepLink);
                for (var pt : tokens) {
                    pushSender.send(pt.getPushToken(), title, body, data);
                }
            } catch (Exception ex) {
                log.warn("[GroupProject] Notification failed for member {} on project {}: {}",
                        member.getUserId(), gp.getId().value(), ex.getMessage());
            }
        }
    }

    private String resolveUsername(UUID userId) {
        try {
            return userRepository.findById(userId)
                    .map(u -> u.getUsername())
                    .orElse("A member");
        } catch (Exception ex) {
            return "A member";
        }
    }
}
