package com.claimsphere.security;

import com.claimsphere.user.domain.Role;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the {@link CurrentUser} from the security context populated by the JWT resource server.
 */
@Component
public class CurrentUserProvider {

    public CurrentUser get() {
        return find().orElseThrow(() -> new AuthenticationCredentialsNotFoundException("No authenticated user"));
    }

    /** The authenticated user, or empty for anonymous requests and background/system work. */
    public Optional<CurrentUser> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return Optional.empty();
        }
        var jwt = jwtAuthentication.getToken();
        List<String> roles = jwt.getClaimAsStringList(JwtTokenService.ROLES_CLAIM);
        if (roles == null || roles.isEmpty()) {
            throw new AuthenticationCredentialsNotFoundException("Token has no role");
        }
        return Optional.of(new CurrentUser(UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString(JwtTokenService.EMAIL_CLAIM),
                Role.valueOf(roles.getFirst())));
    }
}
