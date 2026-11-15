package com.claimsphere.user.domain;

/**
 * Platform roles. Roles are assigned server-side only and never accepted from self-registration requests.
 */
public enum Role {
    CLAIMANT,
    ADJUSTER,
    ADMIN
}
