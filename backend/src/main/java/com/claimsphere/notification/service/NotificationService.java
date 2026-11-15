package com.claimsphere.notification.service;

import com.claimsphere.claim.event.ClaimEvent;
import com.claimsphere.common.exception.ResourceNotFoundException;
import com.claimsphere.notification.domain.Notification;
import com.claimsphere.notification.dto.NotificationResponse;
import com.claimsphere.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    /**
     * Creates notifications for the parties affected by a claim event. Idempotent per (eventId, recipient),
     * so redelivered Kafka messages do not produce duplicates.
     */
    @Transactional
    public int createForClaimEvent(ClaimEvent event) {
        int created = 0;
        for (Map.Entry<UUID, String> recipient : recipientsAndMessages(event).entrySet()) {
            UUID userId = recipient.getKey();
            if (notificationRepository.existsByEventIdAndUserId(event.eventId(), userId)) {
                log.info("Skipping duplicate notification eventId={} userId={}", event.eventId(), userId);
                continue;
            }
            notificationRepository.save(new Notification(userId, event.claimId(), event.eventId(),
                    event.eventType().name(), recipient.getValue(), clock.instant()));
            created++;
        }
        log.info("Processed claim event eventId={} type={} claimId={} notificationsCreated={}",
                event.eventId(), event.eventType(), event.claimId(), created);
        return created;
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listForUser(UUID userId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByUserIdAndReadFalse(userId, pageable)
                : notificationRepository.findByUserId(userId, pageable);
        return page.map(NotificationResponse::from);
    }

    @Transactional
    public void markRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        notification.markRead();
    }

    private static Map<UUID, String> recipientsAndMessages(ClaimEvent event) {
        String claim = "Claim " + event.claimNumber();
        Map<UUID, String> messages = new LinkedHashMap<>();
        UUID claimant = event.claimantId();
        UUID adjuster = event.assignedAdjusterId();
        switch (event.eventType()) {
            case CLAIM_SUBMITTED -> messages.put(claimant, claim + " has been submitted.");
            case CLAIM_ASSIGNED -> {
                messages.put(claimant, claim + " is now under review.");
                putIfPresent(messages, adjuster, claim + " has been assigned to you.");
            }
            case CLAIM_APPROVED -> messages.put(claimant, claim + " has been approved for " + event.amount() + ".");
            case CLAIM_REJECTED -> messages.put(claimant, claim + " has been rejected.");
            case CLAIM_SETTLED -> {
                messages.put(claimant, claim + " has been settled for " + event.amount() + ".");
                putIfPresent(messages, adjuster, claim + " has been settled.");
            }
            case CLAIM_WITHDRAWN -> {
                messages.put(claimant, claim + " has been withdrawn.");
                putIfPresent(messages, adjuster, claim + " was withdrawn by the claimant.");
            }
        }
        return messages;
    }

    private static void putIfPresent(Map<UUID, String> messages, UUID userId, String message) {
        if (userId != null) {
            messages.put(userId, message);
        }
    }
}
