package com.example.smartmoney.domain.repositories;

import com.example.smartmoney.domain.models.group.GroupProjectSponsorshipRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupProjectSponsorshipRequestRepository {
    Optional<GroupProjectSponsorshipRequest> findById(UUID id);
    List<GroupProjectSponsorshipRequest> findAllByGroupProjectId(UUID groupProjectId);
    List<GroupProjectSponsorshipRequest> findAllByUserIdAndStatus(UUID userId, String status);
    GroupProjectSponsorshipRequest save(GroupProjectSponsorshipRequest request);
    void saveAll(List<GroupProjectSponsorshipRequest> requests);
    void deleteAll(List<GroupProjectSponsorshipRequest> requests);
}
