package com.claimsphere.common.exception;

/**
 * Thrown when a workflow transition is not permitted from the current state.
 */
public class InvalidStateTransitionException extends ConflictException {

    public InvalidStateTransitionException(Enum<?> from, Enum<?> to) {
        super("INVALID_STATE_TRANSITION", "Cannot transition from %s to %s".formatted(from, to));
    }
}
