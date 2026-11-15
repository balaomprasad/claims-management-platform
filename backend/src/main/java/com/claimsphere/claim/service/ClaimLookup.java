package com.claimsphere.claim.service;

import com.claimsphere.claim.domain.Claim;
import com.claimsphere.claim.repository.ClaimRepository;
import com.claimsphere.common.exception.ResourceNotFoundException;
import com.claimsphere.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Loads a claim with its associations and applies the visibility rule in one place.
 */
@Component
public class ClaimLookup {

    private final ClaimRepository claimRepository;
    private final ClaimAccessPolicy accessPolicy;

    public ClaimLookup(ClaimRepository claimRepository, ClaimAccessPolicy accessPolicy) {
        this.claimRepository = claimRepository;
        this.accessPolicy = accessPolicy;
    }

    public Claim loadVisible(UUID claimId, CurrentUser user) {
        Claim claim = claimRepository.findDetailedById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", claimId));
        accessPolicy.requireVisible(claim, user);
        return claim;
    }

    public Claim loadVisibleByNumber(String claimNumber, CurrentUser user) {
        Claim claim = claimRepository.findDetailedByClaimNumber(claimNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", claimNumber));
        accessPolicy.requireVisible(claim, user);
        return claim;
    }
}
