package com.claimsphere.user.repository;

import com.claimsphere.user.domain.ClaimantProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClaimantProfileRepository extends JpaRepository<ClaimantProfile, UUID> {
}
