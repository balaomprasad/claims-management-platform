package com.claimsphere.user.domain;

/**
 * Platform roles. Roles are assigned server-side only and never accepted from self-registration requests.
 */
public enum Role {
    /** Policyholder who reports incidents and tracks their own claims. */
    CLAIMANT,
    /** Assesses assigned claims, requests information, decides and settles. */
    CLAIMS_OFFICER,
    /** Oversees workload, ageing and liability; may reassign and settle. */
    MANAGER,
    /** Provisions users and policies. */
    ADMIN
}
