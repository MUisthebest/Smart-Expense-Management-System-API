package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.infrastructure.persistence.entities.GroupMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaGroupMemberRepository extends JpaRepository<GroupMemberEntity, UUID> {
    List<GroupMemberEntity> findByGroupId(UUID groupId);
    List<GroupMemberEntity> findByUserId(UUID userId);
    void deleteByGroupId(UUID groupId);
}
