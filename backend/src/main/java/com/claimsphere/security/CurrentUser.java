package com.claimsphere.security;

import com.claimsphere.user.domain.Role;

import java.util.UUID;

/**
 * The authenticated principal, derived exclusively from a verified JWT.
 */
public record CurrentUser(UUID id, String email, Role role) {

    public boolean is(Role expected) {
        return role == expected;
    }
}
