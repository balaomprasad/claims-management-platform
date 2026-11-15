package com.claimsphere.dashboard;

import com.claimsphere.common.domain.Market;
import com.claimsphere.policy.domain.ProductType;
import com.claimsphere.policy.dto.PolicyResponse;
import com.claimsphere.support.IntegrationTestSupport;
import com.claimsphere.user.domain.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uses MALAYSIA and HONG_KONG claims only (no other test files claims in these markets), so aggregates are exact.
 */
class ManagerDashboardIntegrationTest extends IntegrationTestSupport {

    @Test
    void aggregatesWorkloadAndLiabilityPerCurrency() throws Exception {
        TestUser claimant = createUser(Role.CLAIMANT);
        TestUser officer = createUser(Role.CLAIMS_OFFICER);
        TestUser manager = createUser(Role.MANAGER);
        PolicyResponse myPolicy = createActivePolicy(claimant.id(), new BigDecimal("100000"), Market.MALAYSIA,
                ProductType.PROPERTY);
        PolicyResponse hkPolicy = createActivePolicy(claimant.id(), new BigDecimal("100000"), Market.HONG_KONG,
                ProductType.MOTOR);

        submitClaim(claimant, myPolicy, "1000");                                  // SUBMITTED, unassigned
        String underReview = submitClaim(claimant, myPolicy, "2000");
        assign(officer, underReview);                                              // UNDER_REVIEW
        String approved = submitClaim(claimant, myPolicy, "3000");
        assign(officer, approved);
        postAs(officer, "/api/v1/claims/{id}/approve", Map.of("approvedAmount", "2500"), approved)
                .andExpect(status().isOk());                                       // APPROVED 2500
        String settled = submitClaim(claimant, myPolicy, "4000");
        assign(officer, settled);
        postAs(officer, "/api/v1/claims/{id}/approve", Map.of("approvedAmount", "4000"), settled)
                .andExpect(status().isOk());
        postAs(officer, "/api/v1/claims/{id}/settle", Map.of("settledAmount", "3500"), settled)
                .andExpect(status().isOk());                                       // SETTLED 3500
        String withdrawn = submitClaim(claimant, myPolicy, "500");
        postAs(claimant, "/api/v1/claims/{id}/withdraw", null, withdrawn).andExpect(status().isOk());
        submitClaim(claimant, hkPolicy, "700");                                   // HKD exposure

        JsonNode malaysia = dashboard(manager, Map.of("market", "MALAYSIA"));
        assertThat(malaysia.get("totalClaims").asLong()).isEqualTo(5);
        assertThat(malaysia.get("outstandingClaims").asLong()).isEqualTo(3);
        assertThat(malaysia.get("unassignedClaims").asLong()).isEqualTo(1);
        assertThat(malaysia.at("/claimsByStatus/SETTLED").asLong()).isEqualTo(1);
        assertThat(malaysia.at("/claimsByStatus/WITHDRAWN").asLong()).isEqualTo(1);
        assertThat(malaysia.get("liabilityByCurrency")).hasSize(1);
        JsonNode myr = malaysia.get("liabilityByCurrency").get(0);
        assertThat(myr.get("currency").asText()).isEqualTo("MYR");
        assertThat(myr.get("estimatedExposure").decimalValue()).isEqualByComparingTo("3000");
        assertThat(myr.get("approvedLiability").decimalValue()).isEqualByComparingTo("2500");
        assertThat(myr.get("settledAmount").decimalValue()).isEqualByComparingTo("3500");
        assertThat(malaysia.at("/processingTime/closedClaims").asLong()).isEqualTo(1);
        assertThat(malaysia.at("/ageing/buckets/0/claims").asLong()).isEqualTo(3);
        assertThat(malaysia.at("/ageing/beyondThreshold").asLong()).isZero();

        JsonNode officerLoad = null;
        for (JsonNode row : malaysia.get("officerWorkload")) {
            if (row.get("officerId").asText().equals(officer.id().toString())) {
                officerLoad = row;
            }
        }
        assertThat(officerLoad).isNotNull();
        assertThat(officerLoad.get("openClaims").asLong()).isEqualTo(2);
        assertThat(officerLoad.get("decidedClaims").asLong()).isEqualTo(2);
        assertThat(officerLoad.get("settledClaims").asLong()).isEqualTo(1);

        // Currencies are reported separately, never summed.
        JsonNode all = dashboard(manager, Map.of());
        assertThat(all.get("liabilityByCurrency")).extracting(row -> row.get("currency").asText())
                .contains("MYR", "HKD")
                .doesNotHaveDuplicates();
        JsonNode hkd = dashboard(manager, Map.of("currency", "HKD")).get("liabilityByCurrency").get(0);
        assertThat(hkd.get("estimatedExposure").decimalValue()).isEqualByComparingTo("700");
    }

    @Test
    void dashboardIsRestrictedToManagersAndValidatesFilters() throws Exception {
        TestUser officer = createUser(Role.CLAIMS_OFFICER);
        TestUser admin = createUser(Role.ADMIN);
        TestUser manager = createUser(Role.MANAGER);

        mockMvc.perform(get("/api/v1/dashboard/manager").header("Authorization", officer.bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/dashboard/manager").header("Authorization", admin.bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/dashboard/manager").header("Authorization", manager.bearer())
                        .param("submittedFrom", "2026-02-01").param("submittedTo", "2026-01-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_DATE_RANGE"));
        mockMvc.perform(get("/api/v1/dashboard/manager").header("Authorization", manager.bearer())
                        .param("ageingDays", "0"))
                .andExpect(status().isUnprocessableEntity());
    }

    private void assign(TestUser officer, String claimId) throws Exception {
        postAs(officer, "/api/v1/claims/{id}/assign", null, claimId).andExpect(status().isOk());
    }

    private JsonNode dashboard(TestUser manager, Map<String, String> params) throws Exception {
        var request = get("/api/v1/dashboard/manager").header("Authorization", manager.bearer());
        params.forEach(request::param);
        return readJson(mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
