package com.claimsphere.policy.repository;

import com.claimsphere.policy.domain.Policy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PolicyRepository extends JpaRepository<Policy, UUID> {

    @EntityGraph(attributePaths = "holder")
    Page<Policy> findByHolderId(UUID holderId, Pageable pageable);

    @EntityGraph(attributePaths = "holder")
    Page<Policy> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "holder")
    Optional<Policy> findWithHolderById(UUID id);
}
