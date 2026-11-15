package com.claimsphere.claim.dto;

import com.claimsphere.claim.domain.ClaimInformationRequest;
import com.claimsphere.user.domain.User;

import java.time.Instant;
import java.util.UUID;

public record ClaimInformationRequestResponse(UUID id, String message, String requestedByName, Instant requestedAt,
                                              String response, String respondedByName, Instant respondedAt) {

    public static ClaimInformationRequestResponse from(ClaimInformationRequest request) {
        User respondedBy = request.getRespondedBy();
        return new ClaimInformationRequestResponse(request.getId(), request.getMessage(),
                request.getRequestedBy().getFullName(), request.getRequestedAt(), request.getResponse(),
                respondedBy == null ? null : respondedBy.getFullName(), request.getRespondedAt());
    }
}
