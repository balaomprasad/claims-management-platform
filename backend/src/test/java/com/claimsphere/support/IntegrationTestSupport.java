package com.claimsphere.support;

import com.claimsphere.policy.domain.ProductType;
import com.claimsphere.policy.dto.CreatePolicyRequest;
import com.claimsphere.policy.dto.PolicyResponse;
import com.claimsphere.policy.service.PolicyService;
import com.claimsphere.user.domain.Role;
import com.claimsphere.user.domain.User;
import com.claimsphere.user.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared base for API integration tests: full Spring context, Flyway-migrated database and an in-JVM Kafka broker.
 * Each test creates its own users with unique emails, so tests do not depend on execution order or cleanup.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1)
public abstract class IntegrationTestSupport {

    protected static final String PASSWORD = "Str0ngPassw0rd!";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserService userService;

    @Autowired
    protected PolicyService policyService;

    protected record TestUser(UUID id, String email, String token) {
        public String bearer() {
            return "Bearer " + token;
        }
    }

    protected TestUser createUser(Role role) throws Exception {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.claimsphere.local";
        User user = userService.createUser(email, PASSWORD, role.name() + " Tester", role);
        return new TestUser(user.getId(), email, login(email, PASSWORD));
    }

    protected String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    protected PolicyResponse createActivePolicy(UUID holderId, BigDecimal coverage) {
        LocalDate today = todayUtc();
        return policyService.createPolicy(new CreatePolicyRequest(holderId, ProductType.MOTOR, coverage,
                BigDecimal.valueOf(100), today.minusMonths(6), today.plusMonths(6)));
    }

    /**
     * Submits a claim of 800.00 for yesterday's incident and returns its id.
     */
    protected String submitClaim(TestUser claimant, PolicyResponse policy) throws Exception {
        String body = mockMvc.perform(post("/api/v1/claims").header("Authorization", claimant.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("policyId", policy.id(),
                                "incidentDate", todayUtc().minusDays(1).toString(),
                                "description", "Water damage", "claimedAmount", "800"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return readJson(body).get("id").asText();
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    /** The application clock is UTC; test dates must use the same zone to avoid day-boundary flakiness. */
    protected static LocalDate todayUtc() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    protected JsonNode readJson(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
