package com.claimsphere.claim.repository;

import com.claimsphere.claim.domain.ClaimAssessment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClaimAssessmentRepository extends JpaRepository<ClaimAssessment, UUID> {

    @EntityGraph(attributePaths = "officer")
    List<ClaimAssessment> findByClaimIdOrderByCreatedAtAsc(UUID claimId);
}
