package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.ports.notification.PushNotificationSenderPort;
import com.example.smartmoney.application.ports.notification.PushTokenPort;
import com.example.smartmoney.application.usecases.notification.CreateNotificationUseCase;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.GroupMember;
import com.example.smartmoney.domain.models.group.GroupProject;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.repositories.GroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Notifies every joined member (including the admin) that a group project has reached its target.
 * Each member gets a persistent in-app notification (carrying a deep link to the group project) and,
 * if they have push tokens, a push. Per-member failures are logged and never propagate, so a single
 * bad token can't abort the completion flow or roll back the caller's transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyGroupProjectCompletedUseCase {

    private final GroupRepository groupRepository;
    private final CreateNotificationUseCase createNotificationUseCase;
    private final PushTokenPort pushTokenPort;
    private final PushNotificationSenderPort pushSender;

    public void notifyCompleted(GroupProject gp) {
        Group group = groupRepository.findById(GroupId.of(gp.getGroupId().toString())).orElse(null);
        if (group == null) {
            log.warn("[GroupProject] Completed group project {} has no group {} — skipping notifications.",
                    gp.getId().value(), gp.getGroupId());
            return;
        }

        String deepLink = "/group-projects/" + gp.getId().value();
        String title = "Group project completed! 🎉";
        String body = "\"" + gp.getName() + "\" reached its goal. Congratulations!";

        for (GroupMember member : group.joinedMembers()) {
            try {
                createNotificationUseCase.createNotification(member.getUserId().value(), body, deepLink);

                var tokens = pushTokenPort.findByUserId(member.getUserId().toString());
                Map<String, String> data = Map.of("url", deepLink);
                for (var pt : tokens) {
                    pushSender.send(pt.getPushToken(), title, body, data);
                }
            } catch (Exception ex) {
                log.warn("[GroupProject] Completion notification failed for member {} on project {}: {}",
                        member.getUserId(), gp.getId().value(), ex.getMessage());
            }
        }
    }
}
