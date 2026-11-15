package com.claimsphere.claim.repository;

import com.claimsphere.claim.domain.ClaimDecision;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClaimDecisionRepository extends JpaRepository<ClaimDecision, UUID> {

    @EntityGraph(attributePaths = "decidedBy")
    Optional<ClaimDecision> findByClaimId(UUID claimId);
}
