package com.claimsphere.claim.repository;

import com.claimsphere.claim.domain.Claim;
import com.claimsphere.claim.domain.ClaimStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    @EntityGraph(attributePaths = {"policy", "claimant", "assignedAdjuster"})
    Optional<Claim> findDetailedById(UUID id);

    @EntityGraph(attributePaths = {"policy", "claimant", "assignedAdjuster"})
    Page<Claim> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"policy", "claimant", "assignedAdjuster"})
    Page<Claim> findByStatus(ClaimStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"policy", "claimant", "assignedAdjuster"})
    Page<Claim> findByClaimantId(UUID claimantId, Pageable pageable);

    @EntityGraph(attributePaths = {"policy", "claimant", "assignedAdjuster"})
    Page<Claim> findByClaimantIdAndStatus(UUID claimantId, ClaimStatus status, Pageable pageable);

    @Query("select c.status as status, count(c) as total from Claim c group by c.status")
    List<StatusCount> countByStatus();

    @Query(value = "SELECT nextval('claim_number_seq')", nativeQuery = true)
    long nextClaimSequence();

    interface StatusCount {
        ClaimStatus getStatus();

        long getTotal();
    }
}
