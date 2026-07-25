package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.dtos.responses.group.GroupSummaryResponse;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.GroupProject;
import com.example.smartmoney.domain.models.group.enums.GroupStatus;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListMyGroupsUseCase {

    private final GroupRepository groupRepository;
    private final GroupProjectRepository groupProjectRepository;

    @Transactional(readOnly = true)
    public List<GroupSummaryResponse> execute(String actorId) {
        UserId actor = UserId.of(actorId);

        // findAllByMember covers both admin and regular members since admin is also stored
        // as a JOINED member in group_members
        List<Group> groups = groupRepository.findAllByMember(actor);

        return groups.stream()
                .filter(g -> g.getStatus() != GroupStatus.DISSOLVED)
                .sorted(Comparator.comparing(Group::getCreatedAt).reversed())
                .map(g -> {
                    GroupProject gp = groupProjectRepository.findByGroupId(g.getId().value()).orElse(null);
                    return GroupSummaryResponse.fromDomain(g, actor, gp);
                })
                .collect(Collectors.toList());
    }
}

