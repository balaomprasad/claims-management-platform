package com.claimsphere.common.exception;

/**
 * Thrown when a resource does not exist or is not visible to the current user.
 * Ownership failures deliberately use this exception so that resource identifiers cannot be enumerated.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super("%s '%s' was not found".formatted(resource, id));
    }
}
