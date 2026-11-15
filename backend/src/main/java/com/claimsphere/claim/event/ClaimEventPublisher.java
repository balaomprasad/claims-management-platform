package com.claimsphere.claim.event;

import com.claimsphere.outbox.OutboxService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publishes claim events through the transactional outbox: the event is stored atomically with the claim change
 * and relayed to Kafka by {@link com.claimsphere.outbox.OutboxRelay}.
 */
@Component
public class ClaimEventPublisher {

    static final String AGGREGATE_TYPE = "CLAIM";

    private final OutboxService outboxService;
    private final String topic;

    public ClaimEventPublisher(OutboxService outboxService,
                               @Value("${app.kafka.claim-events-topic}") String topic) {
        this.outboxService = outboxService;
        this.topic = topic;
    }

    public void publish(ClaimEvent event) {
        outboxService.enqueue(event.eventId(), topic, AGGREGATE_TYPE, event.claimId(), event.eventType().name(),
                event);
    }
}
