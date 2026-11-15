package com.claimsphere.audit;

import com.claimsphere.outbox.OutboxEventRepository;
import com.claimsphere.support.IntegrationTestSupport;
import com.claimsphere.user.domain.Role;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Audit trail, correlation-id propagation and outbox publication for a claim workflow.
 */
class AuditAndOutboxIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Test
    void workflowActionsAreAuditedWithCorrelationIdAndEventsLeaveTheOutbox() throws Exception {
        TestUser claimant = createUser(Role.CLAIMANT);
        TestUser officer = createUser(Role.CLAIMS_OFFICER);
        TestUser manager = createUser(Role.MANAGER);
        var policy = createActivePolicy(claimant.id(), new BigDecimal("5000"));

        String body = mockMvc.perform(post("/api/v1/claims").header("Authorization", claimant.bearer())
                        .header("X-Correlation-Id", "trace-submit-001")
                        .contentType(MediaType.APPLICATION_JSON).content(json(claimBody(policy, "400"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-Id", "trace-submit-001"))
                .andReturn().getResponse().getContentAsString();
        String claimId = readJson(body).get("id").asText();

        mockMvc.perform(post("/api/v1/claims/{id}/assign", claimId).header("Authorization", officer.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"));

        JsonNode logs = readJson(mockMvc.perform(get("/api/v1/audit-logs").param("entityType", "CLAIM")
                        .param("entityId", claimId).param("sort", "createdAt,asc")
                        .header("Authorization", manager.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("content");
        assertThat(logs).extracting(entry -> entry.get("action").asText())
                .containsExactly("CLAIM_SUBMITTED", "CLAIM_ASSIGNED");
        assertThat(logs.get(0).get("correlationId").asText()).isEqualTo("trace-submit-001");
        assertThat(logs.get(0).get("actorId").asText()).isEqualTo(claimant.id().toString());
        assertThat(logs.get(1).get("actorId").asText()).isEqualTo(officer.id().toString());

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(outboxRepository.findAll()).filteredOn(e -> "trace-submit-001".equals(e.getCorrelationId()))
                        .singleElement()
                        .satisfies(e -> assertThat(e.getPublishedAt()).isNotNull()));

        mockMvc.perform(get("/api/v1/audit-logs").header("Authorization", claimant.bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/audit-logs").header("Authorization", officer.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidCorrelationIdIsReplaced() throws Exception {
        mockMvc.perform(get("/actuator/health").header("X-Correlation-Id", "bad id with spaces"))
                .andExpect(header().string("X-Correlation-Id", matchesPattern("[0-9a-f-]{36}")));
    }
}
