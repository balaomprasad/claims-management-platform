package com.claimsphere.notification.messaging;

import com.claimsphere.claim.event.ClaimEvent;
import com.claimsphere.common.web.CorrelationId;
import com.claimsphere.notification.service.NotificationService;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Consumes claim lifecycle events. Failures are retried and then routed to the dead-letter topic
 * by the error handler configured in {@code KafkaConfig}. Processing is idempotent per event and recipient.
 */
@Component
public class ClaimEventListener {

    private final NotificationService notificationService;

    public ClaimEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "${app.kafka.claim-events-topic}", groupId = "claimsphere-notifications")
    public void onClaimEvent(@Payload ClaimEvent event,
                             @Header(name = CorrelationId.HEADER, required = false) byte[] correlationHeader) {
        String correlationId = correlationHeader != null
                ? new String(correlationHeader, StandardCharsets.UTF_8) : event.correlationId();
        MDC.put(CorrelationId.MDC_KEY, correlationId);
        try {
            notificationService.createForClaimEvent(event);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
