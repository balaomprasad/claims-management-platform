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
    private TestUser officer;
    private TestUser otherOfficer;
    private TestUser manager;
    private TestUser admin;
    private PolicyResponse policy;
    private String claimId;

    @BeforeEach
    void setUp() throws Exception {
        claimant = createUser(Role.CLAIMANT);
        otherClaimant = createUser(Role.CLAIMANT);
        officer = createUser(Role.CLAIMS_OFFICER);
        otherOfficer = createUser(Role.CLAIMS_OFFICER);
        manager = createUser(Role.MANAGER);
        admin = createUser(Role.ADMIN);
        policy = createActivePolicy(claimant.id(), new BigDecimal("10000"));
        claimId = submitClaim(claimant, policy);
    }

    @Test
    void claimantCannotSeeAnotherClaimantsClaimOrPolicy() throws Exception {
        for (String url : new String[]{"/api/v1/claims/{id}", "/api/v1/claims/{id}/history",
                "/api/v1/claims/{id}/information-requests"}) {
            mockMvc.perform(get(url, claimId).header("Authorization", otherClaimant.bearer()))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/api/v1/policies/{id}", policy.id()).header("Authorization", otherClaimant.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/claims").header("Authorization", otherClaimant.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void claimantFiltersCannotWidenScopeToOtherClaimants() throws Exception {
        mockMvc.perform(get("/api/v1/claims").param("unassigned", "true")
                        .header("Authorization", otherClaimant.bearer()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void claimantCannotFileClaimOnAnotherClaimantsPolicy() throws Exception {
        mockMvc.perform(post("/api/v1/claims").header("Authorization", otherClaimant.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(claimBody(policy, "10"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void claimantCannotPerformStaffActionsOrSeeInternalNotes() throws Exception {
        postAs(claimant, "/api/v1/claims/{id}/assign", null, claimId).andExpect(status().isForbidden());
        postAs(claimant, "/api/v1/claims/{id}/approve", Map.of("approvedAmount", "10"), claimId)
                .andExpect(status().isForbidden());
        postAs(claimant, "/api/v1/claims/{id}/settle", Map.of("settledAmount", "10"), claimId)
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/claims/{id}/assessments", claimId).header("Authorization", claimant.bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/claims/summary").header("Authorization", claimant.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCannotSubmitClaims() throws Exception {
        for (TestUser staff : new TestUser[]{officer, manager, admin}) {
            mockMvc.perform(post("/api/v1/claims").header("Authorization", staff.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content(json(claimBody(policy, "10"))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void onlyAssignedOfficerCanDecide() throws Exception {
        postAs(manager, "/api/v1/claims/{id}/assign", Map.of("officerId", officer.id()), claimId)
                .andExpect(status().isOk());

        postAs(otherOfficer, "/api/v1/claims/{id}/approve", Map.of("approvedAmount", "10"), claimId)
                .andExpect(status().isForbidden());
        postAs(manager, "/api/v1/claims/{id}/approve", Map.of("approvedAmount", "10"), claimId)
                .andExpect(status().isForbidden());
        postAs(otherOfficer, "/api/v1/claims/{id}/request-information", Map.of("message", "x"), claimId)
                .andExpect(status().isForbidden());
    }

    @Test
    void officerCannotTakeOverAssignedClaimButManagerCanReassign() throws Exception {
        postAs(officer, "/api/v1/claims/{id}/assign", null, claimId).andExpect(status().isOk());

        postAs(otherOfficer, "/api/v1/claims/{id}/assign", null, claimId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CLAIM_ALREADY_ASSIGNED"));

        postAs(manager, "/api/v1/claims/{id}/assign", Map.of("officerId", otherOfficer.id()), claimId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedOfficerId").value(otherOfficer.id().toString()))
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        mockMvc.perform(get("/api/v1/claims/{id}/assignments", claimId).header("Authorization", manager.bearer()))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void officerCannotAssignSomeoneElseAndAdminCannotAssignNonOfficer() throws Exception {
        postAs(officer, "/api/v1/claims/{id}/assign", Map.of("officerId", otherOfficer.id()), claimId)
                .andExpect(status().isForbidden());
        postAs(admin, "/api/v1/claims/{id}/assign", Map.of("officerId", claimant.id()), claimId)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_USER_ROLE"));
    }

    @Test
    void invalidTransitionsReturn409() throws Exception {
        postAs(officer, "/api/v1/claims/{id}/approve", Map.of("approvedAmount", "10"), claimId)
                .andExpect(status().isForbidden());
        postAs(manager, "/api/v1/claims/{id}/settle", Map.of("settledAmount", "10"), claimId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATE_TRANSITION"));
        postAs(claimant, "/api/v1/claims/{id}/information", Map.of("response", "x"), claimId)
                .andExpect(status().isConflict());

        postAs(officer, "/api/v1/claims/{id}/assign", null, claimId).andExpect(status().isOk());
        postAs(officer, "/api/v1/claims/{id}/settle", Map.of("settledAmount", "10"), claimId)
                .andExpect(status().isConflict());
    }

    @Test
    void claimantCanWithdrawOwnClaimButNotOthers() throws Exception {
        postAs(otherClaimant, "/api/v1/claims/{id}/withdraw", null, claimId).andExpect(status().isNotFound());
        postAs(claimant, "/api/v1/claims/{id}/withdraw", null, claimId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.closedAt").exists());
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
