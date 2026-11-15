package com.claimsphere.claim.service;

import com.claimsphere.claim.domain.Claim;
import com.claimsphere.common.exception.ResourceNotFoundException;
import com.claimsphere.security.CurrentUser;
import com.claimsphere.user.domain.Role;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Resource-level authorisation rules for claims, complementing the role checks on the controller.
 */
@Component
public class ClaimAccessPolicy {

    /**
     * Claimants may only see their own claims; others' claims are reported as not found to avoid enumeration.
     */
    public void requireVisible(Claim claim, CurrentUser user) {
        if (user.is(Role.CLAIMANT) && !claim.isOwnedBy(user.id())) {
            throw new ResourceNotFoundException("Claim", claim.getId());
        }
    }

    public void requireAssignedAdjuster(Claim claim, CurrentUser user) {
        if (!user.is(Role.ADJUSTER) || !claim.isAssignedTo(user.id())) {
            throw new AccessDeniedException("Only the assigned adjuster can decide this claim");
        }
    }

    public void requireOwner(Claim claim, CurrentUser user) {
        requireVisible(claim, user);
        if (!claim.isOwnedBy(user.id())) {
            throw new AccessDeniedException("Only the claimant can perform this action");
        }
    }
}
