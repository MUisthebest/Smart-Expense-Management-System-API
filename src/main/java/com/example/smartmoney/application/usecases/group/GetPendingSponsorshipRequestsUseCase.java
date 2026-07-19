package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.dtos.responses.group.GroupProjectSponsorshipRequestResponse;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.GroupProject;
import com.example.smartmoney.domain.models.group.GroupProjectSponsorshipRequest;
import com.example.smartmoney.domain.models.group.valueobjects.GroupProjectId;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.GroupProjectSponsorshipRequestRepository;
import com.example.smartmoney.domain.repositories.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetPendingSponsorshipRequestsUseCase {

    private final GroupProjectSponsorshipRequestRepository sponsorshipRequestRepository;
    private final GroupProjectRepository groupProjectRepository;
    private final GroupRepository groupRepository;

    @Transactional(readOnly = true)
    public List<GroupProjectSponsorshipRequestResponse> execute(String actorId) {
        List<GroupProjectSponsorshipRequest> requests = sponsorshipRequestRepository.findAllByUserIdAndStatus(UUID.fromString(actorId), "PENDING");
        List<GroupProjectSponsorshipRequestResponse> responses = new ArrayList<>();

        for (GroupProjectSponsorshipRequest req : requests) {
            groupProjectRepository.findById(GroupProjectId.of(req.getGroupProjectId().toString())).ifPresent(gp -> {
                groupRepository.findById(GroupId.of(gp.getGroupId().toString())).ifPresent(group -> {
                    int N = group.joinedMembers().size();
                    BigDecimal originalShare = gp.getTargetAmount().divide(
                            BigDecimal.valueOf(gp.getTotalMonths()).multiply(BigDecimal.valueOf(N)),
                            2, RoundingMode.CEILING
                    );
                    BigDecimal proposedShare = originalShare.add(req.getAskedAmount());

                    responses.add(GroupProjectSponsorshipRequestResponse.builder()
                            .requestId(req.getId().toString())
                            .groupProjectId(gp.getId().value().toString())
                            .groupProjectName(gp.getName())
                            .groupName(group.getName())
                            .askedAmount(req.getAskedAmount())
                            .originalShare(originalShare)
                            .proposedShare(proposedShare)
                            .totalMonths(gp.getTotalMonths())
                            .build());
                });
            });
        }
        return responses;
    }
}
