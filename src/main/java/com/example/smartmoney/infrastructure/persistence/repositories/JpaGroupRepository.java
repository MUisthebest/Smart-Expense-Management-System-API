package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.infrastructure.persistence.entities.GroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaGroupRepository extends JpaRepository<GroupEntity, UUID> {
    List<GroupEntity> findByAdminId(UUID adminId);
}
