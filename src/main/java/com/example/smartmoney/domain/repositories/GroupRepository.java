package com.example.smartmoney.domain.repositories;

import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;

import java.util.List;
import java.util.Optional;

public interface GroupRepository {
    Optional<Group> findById(GroupId id);
    List<Group> findAllByAdmin(UserId adminId);
    List<Group> findAllByMember(UserId userId);
    Group save(Group group);
    void delete(GroupId id);
}
