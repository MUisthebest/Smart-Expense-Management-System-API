package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.dtos.requests.group.CreateGroupProjectRequest;
import com.example.smartmoney.application.dtos.responses.group.GroupProjectDetailResponse;
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
import com.example.smartmoney.domain.models.group.enums.GroupStatus;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.GroupProjectSponsorshipRequestRepository;
import com.example.smartmoney.domain.repositories.GroupRepository;
import com.example.smartmoney.domain.services.SponsorshipAllocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateGroupProjectUseCase {

    private final GroupRepository groupRepository;
    private final GroupProjectRepository groupProjectRepository;
    private final GroupProjectSponsorshipRequestRepository sponsorshipRequestRepository;
    private final SponsorshipAllocationService sponsorshipAllocationService;
    private final PushTokenPort pushTokenPort;
    private final PushNotificationSenderPort pushSender;
    private final CreateNotificationUseCase createNotificationUseCase;

    @Value("${app.bypass-date-gate:false}")
    private boolean bypassDateGate;

    @Transactional
    public GroupProjectDetailResponse execute(String actorId, CreateGroupProjectRequest req) {
        int dayOfMonth = LocalDate.now().getDayOfMonth();
        if (!bypassDateGate && dayOfMonth > 7) {
            throw new ProjectException(
                    "Group projects can only be created during days 1-7 of the month",
                    ProjectErrorCode.PROJECT_CREATION_OUTSIDE_WINDOW);
        }

        Group group = groupRepository.findById(GroupId.of(req.getGroupId()))
                .orElseThrow(() -> new ProjectException("Group not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        if (!UserId.of(actorId).equals(group.getAdminId())) {
            throw new ProjectException("Only group admin can create the group project",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }
        if (group.getStatus() != GroupStatus.LOCKED) {
            throw new ProjectException("Group must be LOCKED before creating a project",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }

        // Ensure no active group project exists yet for this group
        groupProjectRepository.findByGroupId(group.getId().value()).ifPresent(existing -> {
            if (existing.getStatus() != GroupProjectStatus.DISSOLVED) {
                throw new ProjectException("A group project already exists for this group",
                        ProjectErrorCode.PROJECT_PERMISSION_DENIED);
            }
            // Delete all associated sponsorship requests first to prevent foreign key constraint violations
            List<GroupProjectSponsorshipRequest> oldReqs = sponsorshipRequestRepository.findAllByGroupProjectId(existing.getId().value());
            sponsorshipRequestRepository.deleteAll(oldReqs);

            groupProjectRepository.delete(existing.getId());
        });

        // Run sponsorship allocation to verify feasibility
        SponsorshipAllocationService.AllocationResult alloc = sponsorshipAllocationService.allocate(
                req.getTargetAmount(), req.getTotalMonths(), group.joinedMembers()
        );

        if (!alloc.isFeasible()) {
            throw new ProjectException(
                    "Với mức thu nhập hiện tại của nhóm, mục tiêu này chưa thực sự phù hợp. Bạn nên cân nhắc giảm số tiền mục tiêu hoặc kéo dài thời gian tích lũy để đạt được hiệu quả tốt nhất nhé!",
                    ProjectErrorCode.PROJECT_USER_INCOME_REQUIRED
            );
        }

        // Determine if all deficits are pre-approved via auto-sponsorship
        boolean allAutoSponsored = true;
        List<GroupProjectSponsorshipRequest> requestsToCreate = new ArrayList<>();
        List<GroupMember> pendingSurveyMembers = new ArrayList<>();

        for (GroupMember member : group.joinedMembers()) {
            BigDecimal proposed = alloc.getProposedShares().get(member.getUserId().value());
            BigDecimal original = alloc.getOriginalShare();

            if (proposed.compareTo(original) > 0) {
                // Member needs to contribute extra (is a helper)
                BigDecimal extraNeeded = proposed.subtract(original);
                boolean autoSponsor = member.getAutoSponsorEnabled() != null && member.getAutoSponsorEnabled();
                boolean withinLimit = member.getAutoSponsorLimit() == null || extraNeeded.compareTo(member.getAutoSponsorLimit()) <= 0;

                if (autoSponsor && withinLimit) {
                    // Pre-approved by member!
                    // Save request as AGREED directly
                    GroupProjectSponsorshipRequest r = GroupProjectSponsorshipRequest.createNew(
                            null, // will set project id below
                            member.getUserId().value(),
                            extraNeeded
                    );
                    r.accept();
                    requestsToCreate.add(r);
                } else {
                    allAutoSponsored = false;
                    // Needs manual survey
                    GroupProjectSponsorshipRequest r = GroupProjectSponsorshipRequest.createNew(
                            null, // will set project id below
                            member.getUserId().value(),
                            extraNeeded
                    );
                    requestsToCreate.add(r);
                    pendingSurveyMembers.add(member);
                }
            }
        }

        GroupProjectStatus initialStatus = allAutoSponsored ? GroupProjectStatus.ACTIVE : GroupProjectStatus.PENDING_SPONSORSHIP;
        LocalDate deadline = LocalDate.now().plusMonths(req.getTotalMonths());

        GroupProject gp = GroupProject.createNew(
                group.getId().value(),
                req.getName(),
                req.getDescription(),
                req.getTargetAmount(),
                group.totalCapacity(),
                req.getCurrency(),
                req.getTotalMonths(),
                deadline,
                initialStatus
        );

        GroupProject saved = groupProjectRepository.save(gp);

        // Update the sponsorship requests with the saved group project id and save them
        if (!requestsToCreate.isEmpty()) {
            List<GroupProjectSponsorshipRequest> updatedReqs = requestsToCreate.stream()
                    .map(r -> new GroupProjectSponsorshipRequest(
                            r.getId(),
                            saved.getId().value(),
                            r.getUserId(),
                            r.getAskedAmount(),
                            r.getStatus(),
                            r.getCreatedAt(),
                            r.getUpdatedAt()
                    ))
                    .toList();
            sponsorshipRequestRepository.saveAll(updatedReqs);
        }

        if (allAutoSponsored) {
            notifyMembersActive(group, saved);
        } else {
            notifyMembersPendingSponsorship(group, saved, pendingSurveyMembers, alloc.getOriginalShare(), alloc.getProposedShares());
        }

        return GroupProjectDetailResponse.fromDomain(saved, saved.getTargetAmount(), null, null, List.of());
    }

    private void notifyMembersActive(Group group, GroupProject gp) {
        String title = "Group project started!";
        String body = "\"" + gp.getName() + "\" is ready. Open the app to set your personal savings target.";
        String deepLink = "/group-projects/" + gp.getId().value();
        Map<String, String> data = Map.of("url", deepLink);

        for (GroupMember member : group.joinedMembers()) {
            if (member.getUserId().equals(group.getAdminId())) continue;
            try {
                createNotificationUseCase.createNotification(member.getUserId().value(), body, deepLink);

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
        String title = "Cần khảo sát đóng góp giúp";
        String deepLink = "/group-projects/" + gp.getId().value();
        Map<String, String> data = Map.of("url", deepLink);

        for (GroupMember member : pendingSurveyMembers) {
            BigDecimal proposed = proposedShares.get(member.getUserId().value());
            String body = String.format(
                    "Trong nhóm của bạn đang có một thành viên không đủ điều kiện để thực hiện kế hoạch tiết kiệm này, bạn có đồng ý giúp không? Bạn sẽ phải tiết kiệm %s VND/tháng thay vì %s VND/tháng cho project này.",
                    proposed.setScale(0).toString(),
                    originalShare.setScale(0).toString()
            );

            try {
                createNotificationUseCase.createNotification(member.getUserId().value(), body, deepLink);

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
}
