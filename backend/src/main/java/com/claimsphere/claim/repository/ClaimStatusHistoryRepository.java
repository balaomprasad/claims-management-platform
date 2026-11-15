package com.claimsphere.claim.repository;

import com.claimsphere.claim.domain.ClaimStatusHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClaimStatusHistoryRepository extends JpaRepository<ClaimStatusHistory, UUID> {

    @EntityGraph(attributePaths = "changedBy")
    List<ClaimStatusHistory> findByClaimIdOrderByChangedAtAsc(UUID claimId);
}
