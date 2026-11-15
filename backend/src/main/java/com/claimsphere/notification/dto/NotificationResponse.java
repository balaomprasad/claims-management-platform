package com.claimsphere.notification.dto;

import com.claimsphere.notification.domain.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, UUID claimId, String type, String message, boolean read,
                                   Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getClaimId(), notification.getType(),
                notification.getMessage(), notification.isRead(), notification.getCreatedAt());
    }
}
