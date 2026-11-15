package com.claimsphere.document.repository;

import com.claimsphere.document.domain.ClaimDocument;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimDocumentRepository extends JpaRepository<ClaimDocument, UUID> {

    @EntityGraph(attributePaths = "uploadedBy")
    List<ClaimDocument> findByClaimIdOrderByCreatedAtAsc(UUID claimId);

    Optional<ClaimDocument> findByIdAndClaimId(UUID id, UUID claimId);
}
