package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.infrastructure.persistence.entities.GroupProjectSponsorshipRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaGroupProjectSponsorshipRequestRepository extends JpaRepository<GroupProjectSponsorshipRequestEntity, UUID> {
    List<GroupProjectSponsorshipRequestEntity> findAllByGroupProjectId(UUID groupProjectId);
    List<GroupProjectSponsorshipRequestEntity> findAllByUserIdAndStatus(UUID userId, String status);
}
