package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.dtos.requests.group.UpdateAutoSponsorshipRequest;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.GroupMember;
import com.example.smartmoney.domain.models.group.enums.GroupMemberInviteStatus;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateAutoSponsorshipUseCase {

    private final GroupRepository groupRepository;

    @Transactional
    public void execute(String actorId, String groupId, UpdateAutoSponsorshipRequest req) {
        Group group = groupRepository.findById(GroupId.of(groupId))
                .orElseThrow(() -> new ProjectException("Group not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        UserId actor = UserId.of(actorId);

        GroupMember member = group.getMembers().stream()
                .filter(m -> m.getUserId().equals(actor) && m.getInviteStatus() == GroupMemberInviteStatus.JOINED)
                .findFirst()
                .orElseThrow(() -> new ProjectException("You are not a joined member of this group", ProjectErrorCode.PROJECT_PERMISSION_DENIED));

        member.updateAutoSponsorship(req.getEnabled(), req.getLimit());
        groupRepository.save(group);
    }
}
