package com.claimsphere.notification.messaging;

import com.claimsphere.claim.event.ClaimEvent;
import com.claimsphere.notification.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes claim lifecycle events. Failures are retried and then routed to the dead-letter topic
 * by the error handler configured in {@code KafkaConfig}.
 */
@Component
public class ClaimEventListener {

    private final NotificationService notificationService;

    public ClaimEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "${app.kafka.claim-events-topic}", groupId = "claimsphere-notifications")
    public void onClaimEvent(ClaimEvent event) {
        notificationService.createForClaimEvent(event);
    }
}
