package com.claimsphere.claim.web;

import com.claimsphere.common.domain.Market;
import com.claimsphere.policy.domain.ProductType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end officer workflow over HTTP: submit -> assign -> assess -> request info -> respond -> approve -> settle.
 */
class ClaimWorkflowIntegrationTest extends IntegrationTestSupport {

    @Test
    void fullClaimLifecycleWithInformationRequest() throws Exception {
        TestUser claimant = createUser(Role.CLAIMANT);
        TestUser officer = createUser(Role.CLAIMS_OFFICER);
        PolicyResponse policy = createActivePolicy(claimant.id(), new BigDecimal("50000"));

        String created = mockMvc.perform(post("/api/v1/claims").header("Authorization", claimant.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(claimBody(policy, "1200.50"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.claimNumber", matchesPattern("CLM-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.market").value("SINGAPORE"))
                .andExpect(jsonPath("$.currency").value("SGD"))
                .andExpect(jsonPath("$.claimType").value("MOTOR"))
                .andExpect(jsonPath("$.priority").value("NORMAL"))
                .andReturn().getResponse().getContentAsString();
        String claimId = readJson(created).get("id").asText();
        String claimNumber = readJson(created).get("claimNumber").asText();

        // Officer sees it in the unassigned queue and self-assigns.
        mockMvc.perform(get("/api/v1/claims").param("unassigned", "true").param("size", "100")
                        .header("Authorization", officer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(claimId)).exists());
        postAs(officer, "/api/v1/claims/{id}/assign", null, claimId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.assignedOfficerId").value(officer.id().toString()));

        postAs(officer, "/api/v1/claims/{id}/assessments",
                Map.of("notes", "Photos consistent with description", "recommendedAmount", "1000"), claimId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recommendedAmount").value(1000));

        postAs(officer, "/api/v1/claims/{id}/request-information",
                Map.of("message", "Please upload the repair quotation"), claimId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INFO_REQUESTED"));

        // Claimant may correct details while information is requested, then responds.
        mockMvc.perform(put("/api/v1/claims/{id}", claimId).header("Authorization", claimant.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("incidentLocation", "Orchard Road", "incidentDescription",
                                "Water damage to the car interior", "estimatedLoss", "1100"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedLoss").value(1100));
        postAs(claimant, "/api/v1/claims/{id}/information", Map.of("response", "Quotation uploaded"), claimId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        postAs(officer, "/api/v1/claims/{id}/approve",
                Map.of("approvedAmount", "1000.00", "comment", "Excess applied"), claimId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedAmount").value(1000.00));

        postAs(officer, "/api/v1/claims/{id}/settle",
                Map.of("settledAmount", "1000.00", "settlementReference", "PAY-123"), claimId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.settledAmount").value(1000.00))
                .andExpect(jsonPath("$.settlementDate").value(todayUtc().toString()))
                .andExpect(jsonPath("$.closedAt").exists());

        JsonNode history = getJson(claimant, "/api/v1/claims/{id}/history", claimId);
        assertThat(history).extracting(entry -> entry.get("toStatus").asText()).containsExactly(
                "SUBMITTED", "UNDER_REVIEW", "INFO_REQUESTED", "UNDER_REVIEW", "APPROVED", "SETTLED");

        mockMvc.perform(get("/api/v1/claims/{id}/decision", claimId).header("Authorization", claimant.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPROVED"))
                .andExpect(jsonPath("$.amount").value(1000.00));

        JsonNode requests = getJson(claimant, "/api/v1/claims/{id}/information-requests", claimId);
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).get("response").asText()).isEqualTo("Quotation uploaded");

        mockMvc.perform(get("/api/v1/claims/by-number/{number}", claimNumber)
                        .header("Authorization", claimant.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(claimId));
    }

    @Test
    void rejectRequiresReasonAndEndsLifecycle() throws Exception {
        TestUser claimant = createUser(Role.CLAIMANT);
        TestUser officer = createUser(Role.CLAIMS_OFFICER);
        String claimId = submitClaim(claimant, createActivePolicy(claimant.id(), new BigDecimal("5000")));
        postAs(officer, "/api/v1/claims/{id}/assign", null, claimId).andExpect(status().isOk());

        postAs(officer, "/api/v1/claims/{id}/reject", Map.of("reason", " "), claimId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        postAs(officer, "/api/v1/claims/{id}/reject", Map.of("reason", "Not covered"), claimId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mockMvc.perform(get("/api/v1/claims/{id}/decision", claimId).header("Authorization", claimant.bearer()))
                .andExpect(jsonPath("$.outcome").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value("Not covered"));

        postAs(claimant, "/api/v1/claims/{id}/withdraw", null, claimId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void submitRejectsClaimAboveCoverageWith422() throws Exception {
        TestUser claimant = createUser(Role.CLAIMANT);
        PolicyResponse policy = createActivePolicy(claimant.id(), new BigDecimal("1000"));

        mockMvc.perform(post("/api/v1/claims").header("Authorization", claimant.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(claimBody(policy, "1000.01"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("AMOUNT_EXCEEDS_COVERAGE"));
    }

    @Test
    void duplicateSubmissionWithSameIdempotencyKeyCreatesOneClaim() throws Exception {
        TestUser claimant = createUser(Role.CLAIMANT);
        PolicyResponse policy = createActivePolicy(claimant.id(), new BigDecimal("5000"));
        String body = json(claimBody(policy, "300"));

        String first = mockMvc.perform(post("/api/v1/claims").header("Authorization", claimant.bearer())
                        .header("Idempotency-Key", "submit-abc-123").contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/v1/claims").header("Authorization", claimant.bearer())
                        .header("Idempotency-Key", "submit-abc-123").contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(readJson(first).get("id").asText()));

        mockMvc.perform(get("/api/v1/claims").header("Authorization", claimant.bearer()))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(post("/api/v1/claims").header("Authorization", claimant.bearer())
                        .header("Idempotency-Key", "bad key!").contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchSupportsMarketTypeAndStatusFilters() throws Exception {
        TestUser claimant = createUser(Role.CLAIMANT);
        TestUser manager = createUser(Role.MANAGER);
        PolicyResponse travel = createActivePolicy(claimant.id(), new BigDecimal("9000"),
                Market.THAILAND, ProductType.TRAVEL);
        String claimId = submitClaim(claimant, travel);

        mockMvc.perform(get("/api/v1/claims").header("Authorization", manager.bearer())
                        .param("market", "THAILAND").param("claimType", "TRAVEL").param("status", "SUBMITTED")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '%s')].currency".formatted(claimId)).value("THB"));
        mockMvc.perform(get("/api/v1/claims").header("Authorization", manager.bearer())
                        .param("market", "THAILAND").param("claimType", "HEALTH").param("size", "100"))
                .andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(claimId)).doesNotExist());
        mockMvc.perform(get("/api/v1/claims").header("Authorization", manager.bearer()).param("market", "MARS"))
                .andExpect(status().isBadRequest());
    }

    private JsonNode getJson(TestUser user, String url, Object... vars) throws Exception {
        return readJson(mockMvc.perform(get(url, vars).header("Authorization", user.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
