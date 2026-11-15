package com.claimsphere.user.dto;

import com.claimsphere.common.domain.Market;
import com.claimsphere.user.domain.ClaimantProfile;
import com.claimsphere.user.domain.User;

import java.util.UUID;

public record ClaimantProfileResponse(UUID userId, String email, String fullName, Market market, String phone,
                                      String address) {

    public static ClaimantProfileResponse from(User user, ClaimantProfile profile) {
        return new ClaimantProfileResponse(user.getId(), user.getEmail(), user.getFullName(), profile.getMarket(),
                profile.getPhone(), profile.getAddress());
    }
}
