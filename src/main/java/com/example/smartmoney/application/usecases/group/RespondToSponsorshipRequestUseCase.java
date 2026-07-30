package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.ports.notification.PushNotificationSenderPort;
import com.example.smartmoney.application.ports.notification.PushTokenPort;
import com.example.smartmoney.application.usecases.notification.CreateNotificationUseCase;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.GroupMember;
import com.example.smartmoney.domain.models.group.GroupProject;
import com.example.smartmoney.domain.models.group.GroupProjectSponsorshipRequest;
import com.example.smartmoney.domain.models.group.enums.GroupProjectStatus;
import com.example.smartmoney.domain.models.group.valueobjects.GroupProjectId;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.GroupProjectSponsorshipRequestRepository;
import com.example.smartmoney.domain.repositories.GroupRepository;
import com.example.smartmoney.domain.services.SponsorshipAllocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RespondToSponsorshipRequestUseCase {

    private final GroupProjectSponsorshipRequestRepository sponsorshipRequestRepository;
    private final GroupProjectRepository groupProjectRepository;
    private final GroupRepository groupRepository;
    private final SponsorshipAllocationService sponsorshipAllocationService;
    private final PushTokenPort pushTokenPort;
    private final PushNotificationSenderPort pushSender;
    private final CreateNotificationUseCase createNotificationUseCase;

    @Transactional
    public void execute(String actorId, String requestId, boolean agreed) {
        UUID reqId = UUID.fromString(requestId);
        GroupProjectSponsorshipRequest request = sponsorshipRequestRepository.findById(reqId)
                .orElseThrow(() -> new ProjectException("Sponsorship request not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        if (!actorId.equals(request.getUserId().toString())) {
            throw new ProjectException("You are not authorized to respond to this request", ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }

        if (!"PENDING".equals(request.getStatus())) {
            throw new ProjectException("This request has already been responded to", ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }

        GroupProject gp = groupProjectRepository.findById(GroupProjectId.of(request.getGroupProjectId().toString()))
                .orElseThrow(() -> new ProjectException("Group project not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        if (gp.getStatus() != GroupProjectStatus.PENDING_SPONSORSHIP) {
            throw new ProjectException("Group project is not in pending sponsorship state", ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }

        Group group = groupRepository.findById(GroupId.of(gp.getGroupId().toString()))
                .orElseThrow(() -> new ProjectException("Group not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        if (agreed) {
            request.accept();
            sponsorshipRequestRepository.save(request);

            // Check if all sponsorship requests are now resolved
            List<GroupProjectSponsorshipRequest> allReqs = sponsorshipRequestRepository.findAllByGroupProjectId(gp.getId().value());
            boolean allAgreed = allReqs.stream().allMatch(r -> "AGREED".equals(r.getStatus()));

            if (allAgreed) {
                gp.activate();
                groupProjectRepository.save(gp);
                notifyMembersActive(group, gp);
            }
        } else {
            request.decline();
            sponsorshipRequestRepository.save(request);

            // Handle decline: Recalculate allocation among members who have agreed so far
            List<GroupProjectSponsorshipRequest> allReqs = sponsorshipRequestRepository.findAllByGroupProjectId(gp.getId().value());
            
            List<UUID> agreedUserIds = allReqs.stream()
                    .filter(r -> "AGREED".equals(r.getStatus()))
                    .map(GroupProjectSponsorshipRequest::getUserId)
                    .toList();

            int N = group.joinedMembers().size();
            BigDecimal originalShare = gp.getTargetAmount().divide(
                    BigDecimal.valueOf(gp.getTotalMonths()).multiply(BigDecimal.valueOf(N)),
                    2, RoundingMode.CEILING
            );

            // We filter deficit members (who need help)
            List<UUID> deficitUserIds = new ArrayList<>();
            for (GroupMember member : group.joinedMembers()) {
                BigDecimal cap = member.getCapacitySnapshot() != null ? member.getCapacitySnapshot() : BigDecimal.ZERO;
                if (cap.compareTo(originalShare) < 0) {
                    deficitUserIds.add(member.getUserId().value());
                }
            }

            // Map members list for allocate: non-deficit and non-agreed helpers get their capacity capped at originalShare
            List<GroupMember> mappedMembers = new ArrayList<>();
            for (GroupMember m : group.joinedMembers()) {
                if (deficitUserIds.contains(m.getUserId().value()) || agreedUserIds.contains(m.getUserId().value())) {
                    mappedMembers.add(m);
                } else {
                    mappedMembers.add(new GroupMember(
                            m.getId(), m.getUserId(), m.getRole(), m.getInviteStatus(),
                            originalShare, m.getAutoSponsorEnabled(), m.getAutoSponsorLimit(),
                            m.getJoinedAt(), m.getCreatedAt(), m.getUpdatedAt()
                    ));
                }
            }

            SponsorshipAllocationService.AllocationResult alloc = sponsorshipAllocationService.allocate(
                    gp.getTargetAmount(), gp.getTotalMonths(), mappedMembers
            );

            if (alloc.isFeasible()) {
                // Delete old requests for this project
                sponsorshipRequestRepository.deleteAll(allReqs);

                // Create new requests for agreed helpers
                boolean allAutoApproved = true;
                List<GroupProjectSponsorshipRequest> newReqs = new ArrayList<>();
                List<GroupMember> pendingSurveyMembers = new ArrayList<>();

                for (GroupMember member : group.joinedMembers()) {
                    BigDecimal proposed = alloc.getProposedShares().get(member.getUserId().value());
                    if (proposed.compareTo(originalShare) > 0) {
                        BigDecimal extraNeeded = proposed.subtract(originalShare);
                        boolean autoSponsor = member.getAutoSponsorEnabled() != null && member.getAutoSponsorEnabled();
                        boolean withinLimit = member.getAutoSponsorLimit() == null || extraNeeded.compareTo(member.getAutoSponsorLimit()) <= 0;

                        if (autoSponsor && withinLimit) {
                            GroupProjectSponsorshipRequest r = GroupProjectSponsorshipRequest.createNew(
                                    gp.getId().value(),
                                    member.getUserId().value(),
                                    extraNeeded
                            );
                            r.accept();
                            newReqs.add(r);
                        } else {
                            allAutoApproved = false;
                            GroupProjectSponsorshipRequest r = GroupProjectSponsorshipRequest.createNew(
                                    gp.getId().value(),
                                    member.getUserId().value(),
                                    extraNeeded
                            );
                            newReqs.add(r);
                            pendingSurveyMembers.add(member);
                        }
                    }
                }

                sponsorshipRequestRepository.saveAll(newReqs);

                if (allAutoApproved) {
                    gp.activate();
                    groupProjectRepository.save(gp);
                    notifyMembersActive(group, gp);
                } else {
                    notifyMembersPendingSponsorship(group, gp, pendingSurveyMembers, originalShare, alloc.getProposedShares());
                }
            } else {
                // Delete all associated sponsorship requests first to prevent foreign key constraint violations
                List<GroupProjectSponsorshipRequest> requests = sponsorshipRequestRepository.findAllByGroupProjectId(gp.getId().value());
                sponsorshipRequestRepository.deleteAll(requests);

                // Sponsorship failed! Delete the project directly so that the unique constraint is released and admin can re-create immediately
                groupProjectRepository.delete(gp.getId());
                notifyAdminFailed(group, gp);
            }
        }
    }

    private void notifyMembersActive(Group group, GroupProject gp) {
        String title = "Group project started!";
        String body = "\"" + gp.getName() + "\" is ready. Open the app to set your personal savings target.";
        // In-app content is a pipe-delimited i18n key the FE localizes; push keeps the raw body.
        String content = "notification.group.project_started|" + gp.getName();
        String deepLink = "/group-projects/" + gp.getId().value();
        Map<String, String> data = Map.of("url", deepLink);

        for (GroupMember member : group.joinedMembers()) {
            if (member.getUserId().equals(group.getAdminId())) continue;
            try {
                createNotificationUseCase.createNotification(member.getUserId().value(), content, deepLink);

                var tokens = pushTokenPort.findByUserId(member.getUserId().toString());
                for (var pt : tokens) {
                    pushSender.send(pt.getPushToken(), title, body, data);
                }
            } catch (Exception ex) {
                log.warn("[GroupProject] Notification failed for member {}: {}",
                        member.getUserId(), ex.getMessage());
            }
        }
    }

    private void notifyMembersPendingSponsorship(Group group, GroupProject gp, List<GroupMember> pendingSurveyMembers, BigDecimal originalShare, Map<UUID, BigDecimal> proposedShares) {
        String title = "Yêu cầu gánh vác đóng góp (vòng mới)";
        String deepLink = "/group-projects/" + gp.getId().value();
        Map<String, String> data = Map.of("url", deepLink);

        for (GroupMember member : pendingSurveyMembers) {
            BigDecimal proposed = proposedShares.get(member.getUserId().value());
            String body = String.format(
                    "Một số thành viên đã từ chối đóng góp giúp. Mức đóng góp mới đề xuất cho bạn là %s VND/tháng thay vì %s VND/tháng cho project này. Bạn đồng ý chứ?",
                    proposed.setScale(0).toString(),
                    originalShare.setScale(0).toString()
            );
            // In-app content is a pipe-delimited i18n key the FE localizes; push keeps the raw body.
            String content = "notification.group.sponsorship_new_round|"
                    + proposed.setScale(0) + "|" + originalShare.setScale(0);

            try {
                createNotificationUseCase.createNotification(member.getUserId().value(), content, deepLink);

                var tokens = pushTokenPort.findByUserId(member.getUserId().toString());
                for (var pt : tokens) {
                    pushSender.send(pt.getPushToken(), title, body, data);
                }
            } catch (Exception ex) {
                log.warn("[GroupProject] Sponsorship Survey Notification failed for member {}: {}",
                        member.getUserId(), ex.getMessage());
            }
        }
    }

    private void notifyAdminFailed(Group group, GroupProject gp) {
        String title = "Dự án nhóm thất bại do không đủ đóng góp giúp";
        String body = String.format(
                "Dự án \"%s\" của bạn đã thất bại do các thành viên không đủ khả năng đóng góp giúp. Hãy thay đổi mục tiêu nhé!",
                gp.getName()
        );
        String deepLink = "/projects?tab=groups";
        Map<String, String> data = Map.of("url", deepLink);
        // In-app content is a pipe-delimited i18n key the FE localizes; push keeps the raw body.
        String content = "notification.group.sponsorship_failed|" + gp.getName();

        try {
            createNotificationUseCase.createNotification(group.getAdminId().value(), content, deepLink);

            var tokens = pushTokenPort.findByUserId(group.getAdminId().toString());
            for (var pt : tokens) {
                pushSender.send(pt.getPushToken(), title, body, data);
            }
        } catch (Exception ex) {
            log.warn("[GroupProject] Fail Notification failed for Admin {}: {}",
                    group.getAdminId(), ex.getMessage());
        }
    }
}
