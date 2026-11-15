package com.claimsphere.claim.repository;

import com.claimsphere.claim.domain.Claim;
import com.claimsphere.claim.domain.ClaimStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimRepository extends JpaRepository<Claim, UUID>, JpaSpecificationExecutor<Claim> {

    @EntityGraph(attributePaths = {"policy", "claimant", "assignedOfficer"})
    Optional<Claim> findDetailedById(UUID id);

    @EntityGraph(attributePaths = {"policy", "claimant", "assignedOfficer"})
    Optional<Claim> findDetailedByClaimNumber(String claimNumber);

    @EntityGraph(attributePaths = {"policy", "claimant", "assignedOfficer"})
    Optional<Claim> findByClaimantIdAndSubmissionKey(UUID claimantId, String submissionKey);

    @Override
    @NonNull
    @EntityGraph(attributePaths = {"policy", "claimant", "assignedOfficer"})
    Page<Claim> findAll(Specification<Claim> spec, @NonNull Pageable pageable);

    @Query("select c.status as status, count(c) as total from Claim c group by c.status")
    List<StatusCount> countByStatus();

    @Query(value = "SELECT nextval('claim_number_seq')", nativeQuery = true)
    long nextClaimSequence();

    interface StatusCount {
        ClaimStatus getStatus();

        long getTotal();
    }
}
