package com.claimsphere.claim.service;

import com.claimsphere.claim.domain.Claim;
import com.claimsphere.claim.dto.CreateClaimRequest;
import com.claimsphere.claim.event.ClaimEventType;
import com.claimsphere.claim.repository.ClaimInformationRequestRepository;
import com.claimsphere.claim.repository.ClaimRepository;
import com.claimsphere.common.domain.CurrencyCode;
import com.claimsphere.common.domain.Market;
import com.claimsphere.common.exception.BusinessRuleException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimSubmissionServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private ClaimRepository claimRepository;
    @Mock
    private ClaimInformationRequestRepository informationRequestRepository;
    @Mock
    private PolicyService policyService;
    @Mock
    private UserService userService;
    @Mock
    private ClaimLookup claimLookup;
    @Mock
    private ClaimNumberGenerator claimNumberGenerator;
    @Mock
    private ClaimTransitionRecorder recorder;

    private ClaimSubmissionService service;
    private User claimant;
    private Policy policy;

    @BeforeEach
    void setUp() {
        service = new ClaimSubmissionService(claimRepository, informationRequestRepository, policyService,
                userService, claimLookup, new ClaimAccessPolicy(), claimNumberGenerator, recorder, CLOCK);
        claimant = new User("casey@example.com", "hash", "Casey", Role.CLAIMANT);
        ReflectionTestUtils.setField(claimant, "id", UUID.randomUUID());
        policy = new Policy("POL-1", claimant, ProductType.MOTOR, Market.SINGAPORE, CurrencyCode.SGD,
                new BigDecimal("10000"), new BigDecimal("100"), TODAY.minusMonths(6), TODAY.plusMonths(6));
        ReflectionTestUtils.setField(policy, "id", UUID.randomUUID());
        lenient().when(policyService.getVisiblePolicy(eq(policy.getId()), any())).thenReturn(policy);
        lenient().when(userService.getReference(claimant.getId())).thenReturn(claimant);
        lenient().when(claimNumberGenerator.next()).thenReturn("CLM-2026-000001");
    }

    @Test
    void submitCreatesClaimInPolicyMarketAndCurrency() {
        var result = service.submit(request(TODAY.minusDays(2), "2500"), "key-12345678", principal());

        assertThat(result.created()).isTrue();
        assertThat(result.claim().market()).isEqualTo(Market.SINGAPORE);
        assertThat(result.claim().currency()).isEqualTo(CurrencyCode.SGD);
        assertThat(result.claim().estimatedLoss()).isEqualByComparingTo("2500.00");
        verify(recorder).record(any(Claim.class), isNull(), eq(claimant), isNull(), eq(ClaimEventType.CLAIM_SUBMITTED));
    }

    @Test
    void repeatedIdempotencyKeyReturnsExistingClaimWithoutCreatingAnother() {
        Claim existing = new Claim("CLM-2026-000009", policy, claimant, TODAY, "x", "y", BigDecimal.TEN, "key-12345678");
        when(claimRepository.findByClaimantIdAndSubmissionKey(claimant.getId(), "key-12345678"))
                .thenReturn(Optional.of(existing));

        var result = service.submit(request(TODAY, "10"), "key-12345678", principal());

        assertThat(result.created()).isFalse();
        assertThat(result.claim().claimNumber()).isEqualTo("CLM-2026-000009");
        verify(recorder, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsEstimatedLossAboveCoverage() {
        assertThatThrownBy(() -> service.submit(request(TODAY, "10000.01"), null, principal()))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "AMOUNT_EXCEEDS_COVERAGE");
    }

    @Test
    void rejectsFutureIncidentDate() {
        assertThatThrownBy(() -> service.submit(request(TODAY.plusDays(1), "10"), null, principal()))
                .hasFieldOrPropertyWithValue("errorCode", "INCIDENT_DATE_IN_FUTURE");
    }

    @Test
    void rejectsIncidentOutsidePolicyPeriod() {
        assertThatThrownBy(() -> service.submit(request(TODAY.minusYears(1), "10"), null, principal()))
                .hasFieldOrPropertyWithValue("errorCode", "INCIDENT_OUTSIDE_POLICY_PERIOD");
    }

    private CreateClaimRequest request(LocalDate incidentDate, String amount) {
        return new CreateClaimRequest(policy.getId(), incidentDate, "Orchard Road", "Collision",
                new BigDecimal(amount));
    }

    private CurrentUser principal() {
        return new CurrentUser(claimant.getId(), claimant.getEmail(), Role.CLAIMANT);
    }
}
