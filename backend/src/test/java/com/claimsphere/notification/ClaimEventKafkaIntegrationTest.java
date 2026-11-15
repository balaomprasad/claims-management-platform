package com.claimsphere.notification;

import com.claimsphere.support.IntegrationTestSupport;
import com.claimsphere.user.domain.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the full event path: claim transition -> after-commit Kafka publish -> consumer -> notifications API.
 */
class ClaimEventKafkaIntegrationTest extends IntegrationTestSupport {

    @Test
    void claimTransitionsProduceNotificationsThroughKafka() throws Exception {
        TestUser claimant = createUser(Role.CLAIMANT);
        TestUser officer = createUser(Role.CLAIMS_OFFICER);
        TestUser stranger = createUser(Role.CLAIMANT);
        String claimId = submitClaim(claimant, createActivePolicy(claimant.id(), new BigDecimal("5000")));

        mockMvc.perform(post("/api/v1/claims/{id}/assign", claimId).header("Authorization", officer.bearer()))
                .andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notifications(claimant)).hasSize(2));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notifications(officer)).hasSize(1));

        JsonNode officerNotification = notifications(officer).get(0);
        assertThat(officerNotification.get("type").asText()).isEqualTo("CLAIM_ASSIGNED");
        assertThat(officerNotification.get("claimId").asText()).isEqualTo(claimId);
        assertThat(officerNotification.get("read").asBoolean()).isFalse();

        String notificationId = officerNotification.get("id").asText();
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", notificationId)
                        .header("Authorization", stranger.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", notificationId)
                        .header("Authorization", officer.bearer()))
                .andExpect(status().isNoContent());
        assertThat(notifications(officer).get(0).get("read").asBoolean()).isTrue();
        assertThat(notifications(stranger)).isEmpty();
    }

    private JsonNode notifications(TestUser user) throws Exception {
        String body = mockMvc.perform(get("/api/v1/notifications").header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return readJson(body).get("content");
    }
}
