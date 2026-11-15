package com.claimsphere.outbox;

import com.claimsphere.common.web.CorrelationId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Enqueues integration events. Must be called inside the business transaction ({@link Propagation#MANDATORY}).
 */
@Service
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(UUID eventId, String topic, String aggregateType, UUID aggregateId, String eventType,
                        Object payload) {
        repository.save(new OutboxEvent(eventId, aggregateType, aggregateId, eventType, topic,
                aggregateId.toString(), serialize(payload), CorrelationId.current(), clock.instant()));
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Event payload could not be serialised", e);
        }
    }
}
