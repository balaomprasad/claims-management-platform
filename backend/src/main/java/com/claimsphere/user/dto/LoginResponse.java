package com.claimsphere.user.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {

    public static LoginResponse bearer(String accessToken, long expiresInSeconds, UserResponse user) {
        return new LoginResponse(accessToken, "Bearer", expiresInSeconds, user);
    }
}
