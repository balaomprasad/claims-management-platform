package com.claimsphere.claim.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Forwards claim events to Kafka only after the originating database transaction has committed,
 * so consumers never observe events for rolled-back changes.
 * <p>
 * Trade-off: without a transactional outbox, an event can be lost if Kafka is unavailable at commit time.
 * Failures are logged with identifiers to allow reconciliation.
 */
@Component
public class ClaimEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ClaimEventPublisher.class);

    private final KafkaTemplate<String, ClaimEvent> kafkaTemplate;
    private final String topic;

    public ClaimEventPublisher(KafkaTemplate<String, ClaimEvent> kafkaTemplate,
                               @Value("${app.kafka.claim-events-topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ClaimEvent event) {
        kafkaTemplate.send(topic, event.claimId().toString(), event).whenComplete((result, error) -> {
            if (error != null) {
                log.error("Failed to publish claim event eventId={} type={} claimId={}",
                        event.eventId(), event.eventType(), event.claimId(), error);
            } else {
                log.info("Published claim event eventId={} type={} claimId={} partition={} offset={}",
                        event.eventId(), event.eventType(), event.claimId(),
                        result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }
}
