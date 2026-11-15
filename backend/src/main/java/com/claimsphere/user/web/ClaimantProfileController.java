package com.claimsphere.user.web;

import com.claimsphere.security.CurrentUserProvider;
import com.claimsphere.user.dto.ClaimantProfileRequest;
import com.claimsphere.user.dto.ClaimantProfileResponse;
import com.claimsphere.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/claimants/me")
@PreAuthorize("hasRole('CLAIMANT')")
public class ClaimantProfileController {

    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;

    public ClaimantProfileController(UserService userService, CurrentUserProvider currentUserProvider) {
        this.userService = userService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ClaimantProfileResponse getProfile() {
        return userService.getClaimantProfile(currentUserProvider.get().id());
    }

    @PutMapping
    public ClaimantProfileResponse updateProfile(@Valid @RequestBody ClaimantProfileRequest request) {
        return userService.updateClaimantProfile(currentUserProvider.get().id(), request);
    }
}
