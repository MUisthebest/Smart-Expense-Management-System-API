package com.example.smartmoney.domain.repositories;

import com.example.smartmoney.domain.models.group.GroupProject;
import com.example.smartmoney.domain.models.group.enums.GroupProjectStatus;
import com.example.smartmoney.domain.models.group.valueobjects.GroupProjectId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupProjectRepository {
    Optional<GroupProject> findById(GroupProjectId id);
    Optional<GroupProject> findByGroupId(UUID groupId);
    List<GroupProject> findAllByStatus(GroupProjectStatus status);
    GroupProject save(GroupProject groupProject);
}
