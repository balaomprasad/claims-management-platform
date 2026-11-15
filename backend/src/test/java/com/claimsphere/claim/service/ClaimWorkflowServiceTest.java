package com.claimsphere.claim.service;

import com.claimsphere.claim.domain.Claim;
import com.claimsphere.claim.domain.ClaimStatus;
import com.claimsphere.claim.domain.ClaimStatusHistory;
import com.claimsphere.claim.dto.ApproveClaimRequest;
import com.claimsphere.claim.dto.AssignClaimRequest;
import com.claimsphere.claim.dto.ClaimActionRequest;
import com.claimsphere.claim.dto.CreateClaimRequest;
import com.claimsphere.claim.event.ClaimEvent;
import com.claimsphere.claim.event.ClaimEventType;
import com.claimsphere.claim.repository.ClaimRepository;
import com.claimsphere.claim.repository.ClaimStatusHistoryRepository;
import com.claimsphere.common.exception.BusinessRuleException;
import com.claimsphere.common.exception.InvalidStateTransitionException;
import com.claimsphere.policy.domain.Policy;
import com.claimsphere.policy.domain.ProductType;
import com.claimsphere.policy.service.PolicyService;
import com.claimsphere.security.CurrentUser;
import com.claimsphere.user.domain.Role;
import com.claimsphere.user.domain.User;
import com.claimsphere.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimWorkflowServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private ClaimRepository claimRepository;
    @Mock
    private ClaimStatusHistoryRepository historyRepository;
    @Mock
    private PolicyService policyService;
    @Mock
    private UserService userService;
    @Mock
    private ClaimNumberGenerator claimNumberGenerator;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ClaimWorkflowService service;

    private User claimant;
    private User adjuster;
    private User otherAdjuster;
    private Policy policy;

    @BeforeEach
    void setUp() {
        service = new ClaimWorkflowService(claimRepository, historyRepository, policyService, userService,
                new ClaimAccessPolicy(), claimNumberGenerator, eventPublisher, CLOCK);
        claimant = user(Role.CLAIMANT);
        adjuster = user(Role.ADJUSTER);
        otherAdjuster = user(Role.ADJUSTER);
        policy = new Policy("POL-1", claimant, ProductType.MOTOR, new BigDecimal("10000.00"),
                new BigDecimal("100.00"), TODAY.minusMonths(6), TODAY.plusMonths(6));
        ReflectionTestUtils.setField(policy, "id", UUID.randomUUID());
        lenient().when(userService.getReference(any())).thenAnswer(inv -> userById(inv.getArgument(0)));
    }

    @Test
    void submitCreatesClaimRecordsHistoryAndRaisesEvent() {
        when(policyService.getVisiblePolicy(policy.getId(), principal(claimant))).thenReturn(policy);
        when(claimNumberGenerator.next()).thenReturn("CLM-2026-000001");

        var response = service.submit(new CreateClaimRequest(policy.getId(), TODAY.minusDays(2),
                "Rear-end collision", new BigDecimal("2500")), principal(claimant));

        assertThat(response.status()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(response.claimedAmount()).isEqualByComparingTo("2500.00");
        ArgumentCaptor<ClaimStatusHistory> history = ArgumentCaptor.forClass(ClaimStatusHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getFromStatus()).isNull();
        assertThat(history.getValue().getToStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        ArgumentCaptor<ClaimEvent> event = ArgumentCaptor.forClass(ClaimEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo(ClaimEventType.CLAIM_SUBMITTED);
        assertThat(event.getValue().claimantId()).isEqualTo(claimant.getId());
    }

    @Test
    void submitRejectsAmountAboveCoverage() {
        when(policyService.getVisiblePolicy(policy.getId(), principal(claimant))).thenReturn(policy);

        assertThatThrownBy(() -> service.submit(new CreateClaimRequest(policy.getId(), TODAY.minusDays(1),
                "Total loss", new BigDecimal("10000.01")), principal(claimant)))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "AMOUNT_EXCEEDS_COVERAGE");
        verify(claimRepository, never()).saveAndFlush(any());
    }

    @Test
    void submitRejectsFutureIncidentDate() {
        when(policyService.getVisiblePolicy(policy.getId(), principal(claimant))).thenReturn(policy);

        assertThatThrownBy(() -> service.submit(new CreateClaimRequest(policy.getId(), TODAY.plusDays(1),
                "Future", new BigDecimal("10")), principal(claimant)))
                .hasFieldOrPropertyWithValue("errorCode", "INCIDENT_DATE_IN_FUTURE");
    }

    @Test
    void submitRejectsIncidentBeforePolicyStart() {
        when(policyService.getVisiblePolicy(policy.getId(), principal(claimant))).thenReturn(policy);

        assertThatThrownBy(() -> service.submit(new CreateClaimRequest(policy.getId(), TODAY.minusYears(1),
                "Old incident", new BigDecimal("10")), principal(claimant)))
                .hasFieldOrPropertyWithValue("errorCode", "INCIDENT_OUTSIDE_POLICY_PERIOD");
    }

    @Test
    void adjusterCannotAssignClaimToAnotherAdjuster() {
        Claim claim = existingClaim();

        assertThatThrownBy(() -> service.assign(claim.getId(),
                new AssignClaimRequest(otherAdjuster.getId(), null), principal(adjuster)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void onlyAssignedAdjusterCanApprove() {
        Claim claim = existingClaim();
        claim.assignTo(adjuster);

        assertThatThrownBy(() -> service.approve(claim.getId(),
                new ApproveClaimRequest(new BigDecimal("100"), null), principal(otherAdjuster)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
    }

    @Test
    void approveCannotExceedClaimedAmount() {
        Claim claim = existingClaim();
        claim.assignTo(adjuster);

        assertThatThrownBy(() -> service.approve(claim.getId(),
                new ApproveClaimRequest(new BigDecimal("2500.01"), null), principal(adjuster)))
                .hasFieldOrPropertyWithValue("errorCode", "APPROVED_AMOUNT_EXCEEDS_CLAIMED");
    }

    @Test
    void approveByAssignedAdjusterRaisesApprovedEvent() {
        Claim claim = existingClaim();
        claim.assignTo(adjuster);

        var response = service.approve(claim.getId(),
                new ApproveClaimRequest(new BigDecimal("2000"), "Within policy terms"), principal(adjuster));

        assertThat(response.status()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(response.approvedAmount()).isEqualByComparingTo("2000.00");
        ArgumentCaptor<ClaimEvent> event = ArgumentCaptor.forClass(ClaimEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo(ClaimEventType.CLAIM_APPROVED);
        assertThat(event.getValue().fromStatus()).isEqualTo("UNDER_REVIEW");
        assertThat(event.getValue().amount()).isEqualByComparingTo("2000.00");
    }

    @Test
    void settlingUnapprovedClaimIsInvalidTransition() {
        Claim claim = existingClaim();

        assertThatThrownBy(() -> service.settle(claim.getId(), ClaimActionRequest.empty(), principal(user(Role.ADMIN))))
                .isInstanceOf(InvalidStateTransitionException.class);
        verify(historyRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void claimantCannotWithdrawSomeoneElsesClaim() {
        Claim claim = existingClaim();
        User otherClaimant = user(Role.CLAIMANT);

        assertThatThrownBy(() -> service.withdraw(claim.getId(), ClaimActionRequest.empty(), principal(otherClaimant)))
                .isInstanceOf(com.claimsphere.common.exception.ResourceNotFoundException.class);
    }

    private Claim existingClaim() {
        Claim claim = new Claim("CLM-2026-000001", policy, claimant, TODAY.minusDays(3), "Hail damage",
                new BigDecimal("2500"));
        ReflectionTestUtils.setField(claim, "id", UUID.randomUUID());
        when(claimRepository.findDetailedById(claim.getId())).thenReturn(Optional.of(claim));
        return claim;
    }

    private User user(Role role) {
        User user = new User(role.name().toLowerCase() + UUID.randomUUID() + "@example.com", "hash", role.name(), role);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private User userById(UUID id) {
        for (User candidate : new User[]{claimant, adjuster, otherAdjuster}) {
            if (candidate.getId().equals(id)) {
                return candidate;
            }
        }
        User unknown = new User("someone@example.com", "hash", "Someone", Role.ADMIN);
        ReflectionTestUtils.setField(unknown, "id", id);
        return unknown;
    }

    private static CurrentUser principal(User user) {
        return new CurrentUser(user.getId(), user.getEmail(), user.getRole());
    }
}
