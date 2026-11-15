package com.claimsphere.claim.service;

import com.claimsphere.audit.service.AuditService;
import com.claimsphere.claim.domain.Claim;
import com.claimsphere.claim.domain.ClaimStatus;
import com.claimsphere.claim.dto.ApproveClaimRequest;
import com.claimsphere.claim.dto.AssessClaimRequest;
import com.claimsphere.claim.dto.AssignClaimRequest;
import com.claimsphere.claim.dto.SettleClaimRequest;
import com.claimsphere.claim.event.ClaimEventType;
import com.claimsphere.claim.repository.ClaimAssessmentRepository;
import com.claimsphere.claim.repository.ClaimAssignmentRepository;
import com.claimsphere.claim.repository.ClaimDecisionRepository;
import com.claimsphere.claim.repository.ClaimInformationRequestRepository;
import com.claimsphere.claim.repository.ClaimRepository;
import com.claimsphere.common.domain.CurrencyCode;
import com.claimsphere.common.domain.Market;
import com.claimsphere.common.exception.ConflictException;
import com.claimsphere.common.exception.InvalidStateTransitionException;
import com.claimsphere.policy.domain.Policy;
import com.claimsphere.policy.domain.ProductType;
import com.claimsphere.security.CurrentUser;
import com.claimsphere.user.domain.Role;
import com.claimsphere.user.domain.User;
import com.claimsphere.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClaimWorkflowServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private ClaimRepository claimRepository;
    @Mock
    private ClaimAssignmentRepository assignmentRepository;
    @Mock
    private ClaimAssessmentRepository assessmentRepository;
    @Mock
    private ClaimDecisionRepository decisionRepository;
    @Mock
    private ClaimInformationRequestRepository informationRequestRepository;
    @Mock
    private UserService userService;
    @Mock
    private ClaimLookup claimLookup;
    @Mock
    private ClaimTransitionRecorder recorder;
    @Mock
    private AuditService auditService;

    private ClaimWorkflowService service;
    private User officer;
    private User otherOfficer;
    private User manager;
    private Claim claim;

    @BeforeEach
    void setUp() {
        service = new ClaimWorkflowService(claimRepository, assignmentRepository, assessmentRepository,
                decisionRepository, informationRequestRepository, userService, claimLookup, new ClaimAccessPolicy(),
                recorder, auditService, CLOCK);
        User claimant = user(Role.CLAIMANT);
        officer = user(Role.CLAIMS_OFFICER);
        otherOfficer = user(Role.CLAIMS_OFFICER);
        manager = user(Role.MANAGER);
        Policy policy = new Policy("POL-1", claimant, ProductType.MOTOR, Market.SINGAPORE, CurrencyCode.SGD,
                new BigDecimal("10000"), BigDecimal.ZERO, TODAY.minusMonths(6), TODAY.plusMonths(6));
        claim = new Claim("CLM-2026-000001", policy, claimant, TODAY.minusDays(3), "Orchard Rd", "Hail damage",
                new BigDecimal("2500"), null);
        ReflectionTestUtils.setField(claim, "id", UUID.randomUUID());
        lenient().when(claimLookup.loadVisible(eq(claim.getId()), any())).thenReturn(claim);
        lenient().when(userService.getReference(any())).thenAnswer(inv -> byId(inv.getArgument(0)));
        lenient().when(userService.getUserWithRole(any(), eq(Role.CLAIMS_OFFICER)))
                .thenAnswer(inv -> byId(inv.getArgument(0)));
    }

    @Test
    void officerSelfAssignsUnassignedClaim() {
        var response = service.assign(claim.getId(), AssignClaimRequest.self(), principal(officer));

        assertThat(response.status()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(response.assignedOfficerId()).isEqualTo(officer.getId());
        verify(assignmentRepository).save(any());
        verify(recorder).record(eq(claim), eq(ClaimStatus.SUBMITTED), any(), any(), eq(ClaimEventType.CLAIM_ASSIGNED));
    }

    @Test
    void officerCannotAssignSomeoneElse() {
        assertThatThrownBy(() -> service.assign(claim.getId(), new AssignClaimRequest(otherOfficer.getId(), null),
                principal(officer))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void officerCannotTakeOverAnAlreadyAssignedClaim() {
        claim.assignTo(officer);

        assertThatThrownBy(() -> service.assign(claim.getId(), AssignClaimRequest.self(), principal(otherOfficer)))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CLAIM_ALREADY_ASSIGNED");
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void managerCanReassignClaimInReview() {
        claim.assignTo(officer);

        var response = service.assign(claim.getId(), new AssignClaimRequest(otherOfficer.getId(), "Workload"),
                principal(manager));

        assertThat(response.assignedOfficerId()).isEqualTo(otherOfficer.getId());
        assertThat(response.status()).isEqualTo(ClaimStatus.UNDER_REVIEW);
    }

    @Test
    void onlyAssignedOfficerCanApprove() {
        claim.assignTo(officer);

        assertThatThrownBy(() -> service.approve(claim.getId(), new ApproveClaimRequest(BigDecimal.TEN, null),
                principal(otherOfficer))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.approve(claim.getId(), new ApproveClaimRequest(BigDecimal.TEN, null),
                principal(manager))).isInstanceOf(AccessDeniedException.class);
        verify(decisionRepository, never()).save(any());
    }

    @Test
    void approvalRecordsDecision() {
        claim.assignTo(officer);

        var response = service.approve(claim.getId(), new ApproveClaimRequest(new BigDecimal("2000"), "OK"),
                principal(officer));

        assertThat(response.approvedAmount()).isEqualByComparingTo("2000.00");
        verify(decisionRepository).save(any());
        verify(recorder).record(eq(claim), eq(ClaimStatus.UNDER_REVIEW), any(), eq("OK"),
                eq(ClaimEventType.CLAIM_APPROVED));
    }

    @Test
    void settlementDateCannotBeInTheFuture() {
        claim.assignTo(officer);
        claim.approve(new BigDecimal("2000"));

        assertThatThrownBy(() -> service.settle(claim.getId(),
                new SettleClaimRequest(new BigDecimal("2000"), TODAY.plusDays(1), null), principal(officer)))
                .hasFieldOrPropertyWithValue("errorCode", "SETTLEMENT_DATE_IN_FUTURE");
    }

    @Test
    void managerCanSettleAndDateDefaultsToToday() {
        claim.assignTo(officer);
        claim.approve(new BigDecimal("2000"));

        var response = service.settle(claim.getId(), new SettleClaimRequest(new BigDecimal("1500"), null, "PAY-1"),
                principal(manager));

        assertThat(response.status()).isEqualTo(ClaimStatus.SETTLED);
        assertThat(response.settlementDate()).isEqualTo(TODAY);
        assertThat(response.settledAmount()).isEqualByComparingTo("1500.00");
    }

    @Test
    void settlingUnapprovedClaimIsInvalidTransition() {
        claim.assignTo(officer);

        assertThatThrownBy(() -> service.settle(claim.getId(),
                new SettleClaimRequest(BigDecimal.TEN, null, null), principal(officer)))
                .isInstanceOf(InvalidStateTransitionException.class);
        verify(recorder, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void assessmentRequiresClaimInReview() {
        assertThatThrownBy(() -> service.assess(claim.getId(), new AssessClaimRequest("notes", null),
                principal(officer))).isInstanceOf(AccessDeniedException.class);

        claim.assignTo(officer);
        claim.approve(new BigDecimal("100"));
        assertThatThrownBy(() -> service.assess(claim.getId(), new AssessClaimRequest("notes", null),
                principal(officer)))
                .hasFieldOrPropertyWithValue("errorCode", "CLAIM_NOT_IN_REVIEW");
    }

    private User byId(UUID id) {
        for (User candidate : new User[]{officer, otherOfficer, manager}) {
            if (candidate.getId().equals(id)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown user " + id);
    }

    private static User user(Role role) {
        User user = new User(UUID.randomUUID() + "@example.com", "hash", role.name(), role);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private static CurrentUser principal(User user) {
        return new CurrentUser(user.getId(), user.getEmail(), user.getRole());
    }
}
