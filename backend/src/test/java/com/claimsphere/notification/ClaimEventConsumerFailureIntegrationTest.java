package com.claimsphere.notification;

import com.claimsphere.claim.event.ClaimEvent;
import com.claimsphere.claim.event.ClaimEventType;
import com.claimsphere.support.IntegrationTestSupport;
import com.claimsphere.user.domain.Role;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consumer failure handling: retries, dead-lettering and idempotent redelivery.
 */
class ClaimEventConsumerFailureIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Value("${app.kafka.claim-events-topic}")
    private String topic;

    @Test
    void malformedMessageIsDeadLetteredWithoutRetry() throws Exception {
        String key = "poison-" + UUID.randomUUID();
        kafkaTemplate.send(topic, key, "{not valid json").get();

        ConsumerRecord<String, byte[]> dead = awaitDeadLetter(key);
        assertThat(new String(dead.value())).isEqualTo("{not valid json");
        assertThat(dead.headers().lastHeader("kafka_dlt-exception-fqcn")).isNotNull();
    }

    @Test
    void eventThatKeepsFailingIsRetriedThenDeadLettered() throws Exception {
        // References a claim and users that do not exist, so every attempt fails on a foreign key.
        UUID claimId = UUID.randomUUID();
        ClaimEvent event = new ClaimEvent(UUID.randomUUID(), ClaimEventType.CLAIM_SUBMITTED, 2, Instant.now(),
                "corr-dlt", claimId, "CLM-0000-000000", UUID.randomUUID(), null, null, "SUBMITTED",
                BigDecimal.TEN, "SGD", UUID.randomUUID());
        kafkaTemplate.send(topic, claimId.toString(), event).get();

        ConsumerRecord<String, byte[]> dead = awaitDeadLetter(claimId.toString());
        assertThat(new String(dead.value())).contains(event.eventId().toString());
        assertThat(new String(dead.headers().lastHeader("kafka_dlt-exception-message").value()))
                .containsIgnoringCase("constraint");
    }

    @Test
    void redeliveredEventCreatesNotificationOnlyOnce() throws Exception {
        TestUser claimant = createUser(Role.CLAIMANT);
        String claimId = submitClaim(claimant, createActivePolicy(claimant.id(), new BigDecimal("5000")));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(notificationCount(claimant)).isEqualTo(1));

        ClaimEvent duplicate = new ClaimEvent(UUID.randomUUID(), ClaimEventType.CLAIM_REJECTED, 2, Instant.now(),
                "corr-dup", UUID.fromString(claimId), "CLM-DUP", claimant.id(), null, "UNDER_REVIEW", "REJECTED",
                BigDecimal.TEN, "SGD", claimant.id());
        kafkaTemplate.send(topic, claimId, duplicate).get();
        kafkaTemplate.send(topic, claimId, duplicate).get();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(notificationCount(claimant)).isEqualTo(2));
        Thread.sleep(1_000);
        assertThat(notificationCount(claimant)).isEqualTo(2);
    }

    private int notificationCount(TestUser user) throws Exception {
        String body = mockMvc.perform(get("/api/v1/notifications").header("Authorization", user.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return readJson(body).get("totalElements").asInt();
    }

    private ConsumerRecord<String, byte[]> awaitDeadLetter(String key) {
        Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-" + UUID.randomUUID(), "false", broker);
        try (Consumer<String, byte[]> consumer = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new ByteArrayDeserializer()).createConsumer()) {
            consumer.subscribe(List.of(topic + ".DLT"));
            AtomicReference<ConsumerRecord<String, byte[]>> found = new AtomicReference<>();
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                for (ConsumerRecord<String, byte[]> record : KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        found.set(record);
                    }
                }
                return found.get() != null;
            });
            return found.get();
        }
    }
}
