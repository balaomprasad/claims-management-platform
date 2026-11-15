package com.claimsphere.claim.repository;

import com.claimsphere.claim.domain.ClaimAssignment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClaimAssignmentRepository extends JpaRepository<ClaimAssignment, UUID> {

    @EntityGraph(attributePaths = {"officer", "assignedBy"})
    List<ClaimAssignment> findByClaimIdOrderByAssignedAtAsc(UUID claimId);
}
