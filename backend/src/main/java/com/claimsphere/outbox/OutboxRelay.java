package com.claimsphere.outbox;

import com.claimsphere.common.web.CorrelationId;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polls the outbox and publishes pending events to Kafka in creation order.
 * <p>
 * Delivery is at-least-once: if the broker acknowledges a send but the database update then fails, the event is
 * sent again on the next poll. Consumers are therefore idempotent on {@code eventId}. On a send failure the batch
 * stops, so later events for the same claim are not published ahead of an earlier one.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final Clock clock;
    private final int batchSize;
    private final long sendTimeoutMs;

    public OutboxRelay(OutboxEventRepository repository, KafkaTemplate<Object, Object> kafkaTemplate, Clock clock,
                       @Value("${app.outbox.batch-size:50}") int batchSize,
                       @Value("${app.outbox.send-timeout-ms:10000}") long sendTimeoutMs) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
        this.batchSize = batchSize;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = repository.findPendingForUpdate(PageRequest.of(0, batchSize));
        for (OutboxEvent event : pending) {
            if (!publish(event)) {
                break;
            }
        }
    }

    private boolean publish(OutboxEvent event) {
        MDC.put(CorrelationId.MDC_KEY, event.getCorrelationId());
        try {
            ProducerRecord<Object, Object> record =
                    new ProducerRecord<>(event.getTopic(), event.getMessageKey(), event.getPayload());
            if (event.getCorrelationId() != null) {
                record.headers().add(CorrelationId.HEADER, event.getCorrelationId().getBytes(StandardCharsets.UTF_8));
            }
            var metadata = kafkaTemplate.send(record).get(sendTimeoutMs, TimeUnit.MILLISECONDS).getRecordMetadata();
            event.markPublished(clock.instant());
            log.info("Published outbox event eventId={} type={} partition={} offset={}",
                    event.getId(), event.getEventType(), metadata.partition(), metadata.offset());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            event.markFailed("Interrupted");
            return false;
        } catch (Exception e) {
            event.markFailed(e.getClass().getSimpleName() + ": " + e.getMessage());
            log.warn("Outbox publish failed eventId={} attempts={} error={}",
                    event.getId(), event.getAttempts(), e.getMessage());
            return false;
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
