package com.claimsphere.claim.repository;

import com.claimsphere.claim.domain.ClaimInformationRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimInformationRequestRepository extends JpaRepository<ClaimInformationRequest, UUID> {

    @EntityGraph(attributePaths = {"requestedBy", "respondedBy"})
    List<ClaimInformationRequest> findByClaimIdOrderByRequestedAtAsc(UUID claimId);

    Optional<ClaimInformationRequest> findFirstByClaimIdAndRespondedAtIsNullOrderByRequestedAtDesc(UUID claimId);
}
