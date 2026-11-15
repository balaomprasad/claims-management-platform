package com.claimsphere.claim.web;

import com.claimsphere.policy.dto.PolicyResponse;
import com.claimsphere.support.IntegrationTestSupport;
import com.claimsphere.user.domain.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end happy path: submit -> assign -> approve -> settle, with history and role-scoped listing.
 */
class ClaimWorkflowIntegrationTest extends IntegrationTestSupport {

    @Test
    void fullClaimLifecycle() throws Exception {
        TestUser claimant = createUser(Role.CLAIMANT);
        TestUser adjuster = createUser(Role.ADJUSTER);
        TestUser admin = createUser(Role.ADMIN);
        PolicyResponse policy = createActivePolicy(claimant.id(), new BigDecimal("50000"));

        String created = mockMvc.perform(post("/api/v1/claims").header("Authorization", claimant.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("policyId", policy.id(),
                                "incidentDate", todayUtc().minusDays(1).toString(),
                                "description", "Windscreen cracked by debris",
                                "claimedAmount", "1200.50"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.claimNumber", matchesPattern("CLM-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.policyNumber").value(policy.policyNumber()))
                .andReturn().getResponse().getContentAsString();
        String claimId = readJson(created).get("id").asText();

        mockMvc.perform(post("/api/v1/claims/{id}/assign", claimId).header("Authorization", adjuster.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.assignedAdjusterId").value(adjuster.id().toString()));

        mockMvc.perform(post("/api/v1/claims/{id}/approve", claimId).header("Authorization", adjuster.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("approvedAmount", "1000.00", "comment", "Excess applied"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedAmount").value(1000.00));

        mockMvc.perform(post("/api/v1/claims/{id}/settle", claimId).header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));

        String history = mockMvc.perform(get("/api/v1/claims/{id}/history", claimId)
                        .header("Authorization", claimant.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode entries = readJson(history);
        assertThat(entries).hasSize(4);
        assertThat(entries.get(3).get("toStatus").asText()).isEqualTo("SETTLED");
        assertThat(entries.get(2).get("comment").asText()).isEqualTo("Excess applied");

        mockMvc.perform(get("/api/v1/claims").header("Authorization", claimant.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(claimId));
    }

    @Test
    void submitRejectsClaimAboveCoverageWith422() throws Exception {
        TestUser claimant = createUser(Role.CLAIMANT);
        PolicyResponse policy = createActivePolicy(claimant.id(), new BigDecimal("1000"));

        mockMvc.perform(post("/api/v1/claims").header("Authorization", claimant.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("policyId", policy.id(),
                                "incidentDate", todayUtc().toString(),
                                "description", "Too expensive",
                                "claimedAmount", "1000.01"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("AMOUNT_EXCEEDS_COVERAGE"));
    }

    @Test
    void rejectRequiresReasonAndEndsLifecycle() throws Exception {
        TestUser claimant = createUser(Role.CLAIMANT);
        TestUser adjuster = createUser(Role.ADJUSTER);
        String claimId = submitClaim(claimant, createActivePolicy(claimant.id(), new BigDecimal("5000")));
        mockMvc.perform(post("/api/v1/claims/{id}/assign", claimId).header("Authorization", adjuster.bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/claims/{id}/reject", claimId).header("Authorization", adjuster.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", " "))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/claims/{id}/reject", claimId).header("Authorization", adjuster.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Not covered"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Not covered"));

        mockMvc.perform(post("/api/v1/claims/{id}/withdraw", claimId).header("Authorization", claimant.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATE_TRANSITION"));
    }
}
