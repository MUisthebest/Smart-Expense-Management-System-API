package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.dtos.requests.group.GetGroupProjectSuggestionsRequest;
import com.example.smartmoney.application.dtos.responses.group.GroupProjectSuggestionsResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.GroupMember;
import com.example.smartmoney.domain.models.group.enums.GroupStatus;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.GroupRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.services.SponsorshipAllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GetGroupProjectSuggestionsUseCase {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final SponsorshipAllocationService sponsorshipAllocationService;

    @Transactional(readOnly = true)
    public GroupProjectSuggestionsResponse execute(String actorId, GetGroupProjectSuggestionsRequest req) {
        Group group = groupRepository.findById(GroupId.of(req.getGroupId()))
                .orElseThrow(() -> new ProjectException("Group not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        if (!UserId.of(actorId).equals(group.getAdminId())) {
            throw new ProjectException("Only group admin can view suggestions",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }
        if (group.getStatus() != GroupStatus.LOCKED) {
            throw new ProjectException("Group must be LOCKED to view suggestions",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }

        BigDecimal A = group.totalCapacity();

        Integer suggestedMonths = null;
        if (req.getInputAmount() != null && req.getInputAmount().compareTo(BigDecimal.ZERO) > 0
                && A.compareTo(BigDecimal.ZERO) > 0) {
            double raw = req.getInputAmount().divide(A, 10, RoundingMode.HALF_UP).doubleValue();
            suggestedMonths = (int) Math.ceil(raw);
        }

        BigDecimal suggestedAmount = null;
        if (req.getInputMonths() != null && req.getInputMonths() > 0) {
            suggestedAmount = A.multiply(BigDecimal.valueOf(req.getInputMonths()))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // Run simulation if possible
        Boolean isFeasible = null;
        BigDecimal totalDeficit = null;
        List<GroupProjectSuggestionsResponse.MemberSimulationDto> sims = null;

        BigDecimal simAmount = req.getInputAmount() != null ? req.getInputAmount() : suggestedAmount;
        Integer simMonths = req.getInputMonths() != null ? req.getInputMonths() : suggestedMonths;

        if (simAmount != null && simMonths != null && simMonths > 0) {
            SponsorshipAllocationService.AllocationResult alloc = sponsorshipAllocationService.allocate(
                    simAmount, simMonths, group.joinedMembers()
            );
            isFeasible = alloc.isFeasible();
            totalDeficit = alloc.getTotalDeficit();
            sims = new java.util.ArrayList<>();
            for (GroupMember member : group.joinedMembers()) {
                BigDecimal originalShare = alloc.getOriginalShare();
                BigDecimal proposedShare = alloc.getProposedShares().get(member.getUserId().value());
                BigDecimal capacity = member.getCapacitySnapshot() != null ? member.getCapacitySnapshot() : BigDecimal.ZERO;
                BigDecimal deficit = originalShare.compareTo(capacity) > 0 ? originalShare.subtract(capacity) : BigDecimal.ZERO;

                var userOpt = userRepository.findById(member.getUserId().value());
                String username = userOpt.map(com.example.smartmoney.domain.models.user.User::getUsername).orElse("unknown");
                String fullName = userOpt.map(com.example.smartmoney.domain.models.user.User::getFullName).orElse("Unknown User");

                sims.add(GroupProjectSuggestionsResponse.MemberSimulationDto.builder()
                        .userId(member.getUserId().toString())
                        .username(username)
                        .fullName(fullName)
                        .capacity(capacity)
                        .originalShare(originalShare)
                        .proposedShare(proposedShare)
                        .deficit(deficit)
                        .needsSponsorship(deficit.compareTo(BigDecimal.ZERO) > 0)
                        .autoSponsorEnabled(member.getAutoSponsorEnabled())
                        .autoSponsorLimit(member.getAutoSponsorLimit())
                        .build());
            }
        }

        return GroupProjectSuggestionsResponse.builder()
                .totalCapacity(A)
                .suggestedMonths(suggestedMonths)
                .suggestedAmount(suggestedAmount)
                .isFeasible(isFeasible)
                .totalDeficit(totalDeficit)
                .memberSimulations(sims)
                .build();
    }
}
