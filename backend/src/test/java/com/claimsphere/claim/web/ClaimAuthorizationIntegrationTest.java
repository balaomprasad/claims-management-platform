package com.claimsphere.claim.web;

import com.claimsphere.policy.dto.PolicyResponse;
import com.claimsphere.support.IntegrationTestSupport;
import com.claimsphere.user.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies role-based and ownership-based authorisation plus invalid state transitions over HTTP.
 */
class ClaimAuthorizationIntegrationTest extends IntegrationTestSupport {

    private TestUser claimant;
    private TestUser otherClaimant;
    private TestUser adjuster;
    private TestUser otherAdjuster;
    private TestUser admin;
    private PolicyResponse policy;
    private String claimId;

    @BeforeEach
    void setUp() throws Exception {
        claimant = createUser(Role.CLAIMANT);
        otherClaimant = createUser(Role.CLAIMANT);
        adjuster = createUser(Role.ADJUSTER);
        otherAdjuster = createUser(Role.ADJUSTER);
        admin = createUser(Role.ADMIN);
        policy = createActivePolicy(claimant.id(), new BigDecimal("10000"));
        claimId = submitClaim(claimant, policy);
    }

    @Test
    void claimantCannotSeeAnotherClaimantsClaimOrPolicy() throws Exception {
        mockMvc.perform(get("/api/v1/claims/{id}", claimId).header("Authorization", otherClaimant.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/claims/{id}/history", claimId).header("Authorization", otherClaimant.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/policies/{id}", policy.id()).header("Authorization", otherClaimant.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/claims").header("Authorization", otherClaimant.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void claimantCannotFileClaimOnAnotherClaimantsPolicy() throws Exception {
        mockMvc.perform(post("/api/v1/claims").header("Authorization", otherClaimant.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("policyId", policy.id(),
                                "incidentDate", todayUtc().toString(),
                                "description", "Not my policy", "claimedAmount", "10"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void claimantCannotPerformAdjusterOrAdminActions() throws Exception {
        mockMvc.perform(post("/api/v1/claims/{id}/assign", claimId).header("Authorization", claimant.bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/claims/{id}/approve", claimId).header("Authorization", claimant.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("approvedAmount", "10"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/claims/{id}/settle", claimId).header("Authorization", claimant.bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/claims/summary").header("Authorization", claimant.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adjusterAndAdminCannotSubmitClaims() throws Exception {
        String body = json(Map.of("policyId", policy.id(), "incidentDate", todayUtc().toString(),
                "description", "x", "claimedAmount", "10"));
        mockMvc.perform(post("/api/v1/claims").header("Authorization", adjuster.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/claims").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyAssignedAdjusterCanApprove() throws Exception {
        mockMvc.perform(post("/api/v1/claims/{id}/assign", claimId).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("adjusterId", adjuster.id()))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/claims/{id}/approve", claimId).header("Authorization", otherAdjuster.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("approvedAmount", "10"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adjusterCannotAssignClaimToSomeoneElse() throws Exception {
        mockMvc.perform(post("/api/v1/claims/{id}/assign", claimId).header("Authorization", adjuster.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("adjusterId", otherAdjuster.id()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotAssignClaimToNonAdjuster() throws Exception {
        mockMvc.perform(post("/api/v1/claims/{id}/assign", claimId).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("adjusterId", claimant.id()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_USER_ROLE"));
    }

    @Test
    void invalidTransitionsReturn409() throws Exception {
        mockMvc.perform(post("/api/v1/claims/{id}/settle", claimId).header("Authorization", admin.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATE_TRANSITION"));

        mockMvc.perform(post("/api/v1/claims/{id}/assign", claimId).header("Authorization", adjuster.bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/claims/{id}/assign", claimId).header("Authorization", adjuster.bearer()))
                .andExpect(status().isConflict());
    }

    @Test
    void claimantCanWithdrawOwnClaimButNotOthers() throws Exception {
        mockMvc.perform(post("/api/v1/claims/{id}/withdraw", claimId).header("Authorization", otherClaimant.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/claims/{id}/withdraw", claimId).header("Authorization", claimant.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }

    @Test
    void unknownClaimReturns404AndMalformedIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/claims/{id}", "00000000-0000-0000-0000-000000000000")
                        .header("Authorization", admin.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/claims/{id}", "not-a-uuid").header("Authorization", admin.bearer()))
                .andExpect(status().isBadRequest());
    }
}
