package com.claimsphere.notification.service;

import com.claimsphere.claim.event.ClaimEvent;
import com.claimsphere.claim.event.ClaimEventType;
import com.claimsphere.notification.domain.Notification;
import com.claimsphere.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService service;

    private final UUID claimantId = UUID.randomUUID();
    private final UUID officerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository,
                Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void assignedEventNotifiesClaimantAndOfficer() {
        ClaimEvent event = event(ClaimEventType.CLAIM_ASSIGNED);

        int created = service.createForClaimEvent(event);

        assertThat(created).isEqualTo(2);
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Notification::getUserId).containsExactly(claimantId, officerId);
        assertThat(saved.getAllValues().get(1).getMessage()).contains("assigned to you");
    }

    @Test
    void redeliveredEventDoesNotCreateDuplicates() {
        ClaimEvent event = event(ClaimEventType.CLAIM_APPROVED);
        when(notificationRepository.existsByEventIdAndUserId(event.eventId(), claimantId)).thenReturn(true);

        int created = service.createForClaimEvent(event);

        assertThat(created).isZero();
        verify(notificationRepository, never()).save(any());
    }

    private ClaimEvent event(ClaimEventType type) {
        return new ClaimEvent(UUID.randomUUID(), type, 2, Instant.now(), "corr-1", UUID.randomUUID(),
                "CLM-2026-000001", claimantId, officerId, "SUBMITTED", "UNDER_REVIEW", new BigDecimal("100.00"), "SGD",
                officerId);
    }
}
