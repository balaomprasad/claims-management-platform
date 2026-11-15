package com.claimsphere.claim.service;

import com.claimsphere.claim.domain.Claim;
import com.claimsphere.claim.domain.ClaimStatus;
import com.claimsphere.claim.domain.ClaimStatusHistory;
import com.claimsphere.claim.dto.ApproveClaimRequest;
import com.claimsphere.claim.dto.AssignClaimRequest;
import com.claimsphere.claim.dto.ClaimActionRequest;
import com.claimsphere.claim.dto.ClaimResponse;
import com.claimsphere.claim.dto.CreateClaimRequest;
import com.claimsphere.claim.dto.RejectClaimRequest;
import com.claimsphere.claim.event.ClaimEvent;
import com.claimsphere.claim.event.ClaimEventType;
import com.claimsphere.claim.repository.ClaimRepository;
import com.claimsphere.claim.repository.ClaimStatusHistoryRepository;
import com.claimsphere.common.exception.BusinessRuleException;
import com.claimsphere.common.exception.ResourceNotFoundException;
import com.claimsphere.policy.domain.Policy;
import com.claimsphere.policy.service.PolicyService;
import com.claimsphere.security.CurrentUser;
import com.claimsphere.user.domain.Role;
import com.claimsphere.user.domain.User;
import com.claimsphere.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Write-side claim operations: submission and every lifecycle transition.
 * Each transition is persisted, recorded in the status history and announced as a {@link ClaimEvent}
 * within a single transaction; the event reaches Kafka only after commit.
 */
@Service
@Transactional
public class ClaimWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(ClaimWorkflowService.class);

    private final ClaimRepository claimRepository;
    private final ClaimStatusHistoryRepository historyRepository;
    private final PolicyService policyService;
    private final UserService userService;
    private final ClaimAccessPolicy accessPolicy;
    private final ClaimNumberGenerator claimNumberGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ClaimWorkflowService(ClaimRepository claimRepository, ClaimStatusHistoryRepository historyRepository,
                                PolicyService policyService, UserService userService,
                                ClaimAccessPolicy accessPolicy, ClaimNumberGenerator claimNumberGenerator,
                                ApplicationEventPublisher eventPublisher, Clock clock) {
        this.claimRepository = claimRepository;
        this.historyRepository = historyRepository;
        this.policyService = policyService;
        this.userService = userService;
        this.accessPolicy = accessPolicy;
        this.claimNumberGenerator = claimNumberGenerator;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public ClaimResponse submit(CreateClaimRequest request, CurrentUser user) {
        Policy policy = policyService.getVisiblePolicy(request.policyId(), user);
        if (!policy.isHeldBy(user.id())) {
            throw new AccessDeniedException("Claims can only be filed against your own policy");
        }
        validateAgainstPolicy(request, policy);

        User claimant = userService.getReference(user.id());
        Claim claim = new Claim(claimNumberGenerator.next(), policy, claimant, request.incidentDate(),
                request.description().trim(), request.claimedAmount());
        recordTransition(claim, null, claimant, null, ClaimEventType.CLAIM_SUBMITTED);
        log.info("Claim submitted claimId={} claimNumber={} policyId={}",
                claim.getId(), claim.getClaimNumber(), policy.getId());
        return ClaimResponse.from(claim);
    }

    public ClaimResponse assign(UUID claimId, AssignClaimRequest request, CurrentUser user) {
        Claim claim = loadVisibleClaim(claimId, user);
        UUID adjusterId = resolveAdjusterId(request.adjusterId(), user);
        User adjuster = userService.getUserWithRole(adjusterId, Role.ADJUSTER);

        ClaimStatus from = claim.getStatus();
        claim.assignTo(adjuster);
        recordTransition(claim, from, userService.getReference(user.id()), request.comment(),
                ClaimEventType.CLAIM_ASSIGNED);
        log.info("Claim assigned claimId={} adjusterId={}", claimId, adjusterId);
        return ClaimResponse.from(claim);
    }

    public ClaimResponse approve(UUID claimId, ApproveClaimRequest request, CurrentUser user) {
        Claim claim = loadVisibleClaim(claimId, user);
        accessPolicy.requireAssignedAdjuster(claim, user);

        ClaimStatus from = claim.getStatus();
        claim.approve(request.approvedAmount());
        recordTransition(claim, from, userService.getReference(user.id()), request.comment(),
                ClaimEventType.CLAIM_APPROVED);
        log.info("Claim approved claimId={} approvedAmount={}", claimId, claim.getApprovedAmount());
        return ClaimResponse.from(claim);
    }

    public ClaimResponse reject(UUID claimId, RejectClaimRequest request, CurrentUser user) {
        Claim claim = loadVisibleClaim(claimId, user);
        accessPolicy.requireAssignedAdjuster(claim, user);

        ClaimStatus from = claim.getStatus();
        String reason = request.reason().trim();
        claim.reject(reason);
        recordTransition(claim, from, userService.getReference(user.id()), reason, ClaimEventType.CLAIM_REJECTED);
        log.info("Claim rejected claimId={}", claimId);
        return ClaimResponse.from(claim);
    }

    public ClaimResponse settle(UUID claimId, ClaimActionRequest request, CurrentUser user) {
        Claim claim = loadVisibleClaim(claimId, user);

        ClaimStatus from = claim.getStatus();
        claim.settle();
        recordTransition(claim, from, userService.getReference(user.id()), request.comment(),
                ClaimEventType.CLAIM_SETTLED);
        log.info("Claim settled claimId={}", claimId);
        return ClaimResponse.from(claim);
    }

    public ClaimResponse withdraw(UUID claimId, ClaimActionRequest request, CurrentUser user) {
        Claim claim = loadVisibleClaim(claimId, user);
        accessPolicy.requireOwner(claim, user);

        ClaimStatus from = claim.getStatus();
        claim.withdraw();
        recordTransition(claim, from, userService.getReference(user.id()), request.comment(),
                ClaimEventType.CLAIM_WITHDRAWN);
        log.info("Claim withdrawn claimId={}", claimId);
        return ClaimResponse.from(claim);
    }

    private void validateAgainstPolicy(CreateClaimRequest request, Policy policy) {
        if (!policy.isActive()) {
            throw new BusinessRuleException("POLICY_NOT_ACTIVE", "Claims can only be filed against an active policy");
        }
        LocalDate today = LocalDate.now(clock);
        if (request.incidentDate().isAfter(today)) {
            throw new BusinessRuleException("INCIDENT_DATE_IN_FUTURE", "Incident date cannot be in the future");
        }
        if (!policy.covers(request.incidentDate())) {
            throw new BusinessRuleException("INCIDENT_OUTSIDE_POLICY_PERIOD",
                    "Incident date must fall within the policy period %s to %s"
                            .formatted(policy.getStartDate(), policy.getEndDate()));
        }
        if (request.claimedAmount().compareTo(policy.getCoverageAmount()) > 0) {
            throw new BusinessRuleException("AMOUNT_EXCEEDS_COVERAGE",
                    "Claimed amount exceeds the policy coverage of " + policy.getCoverageAmount());
        }
    }

    private UUID resolveAdjusterId(UUID requestedAdjusterId, CurrentUser user) {
        if (user.is(Role.ADJUSTER)) {
            if (requestedAdjusterId != null && !requestedAdjusterId.equals(user.id())) {
                throw new AccessDeniedException("Adjusters can only assign claims to themselves");
            }
            return user.id();
        }
        if (requestedAdjusterId == null) {
            throw new BusinessRuleException("ADJUSTER_REQUIRED", "adjusterId is required");
        }
        return requestedAdjusterId;
    }

    private Claim loadVisibleClaim(UUID claimId, CurrentUser user) {
        Claim claim = claimRepository.findDetailedById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", claimId));
        accessPolicy.requireVisible(claim, user);
        return claim;
    }

    private void recordTransition(Claim claim, ClaimStatus from, User actor, String comment, ClaimEventType type) {
        // Flush so optimistic-lock conflicts surface here and audit timestamps are populated for the response.
        claimRepository.saveAndFlush(claim);
        historyRepository.save(new ClaimStatusHistory(claim, from, claim.getStatus(), actor,
                blankToNull(comment), clock.instant()));
        eventPublisher.publishEvent(toEvent(claim, from, actor.getId(), type));
    }

    private ClaimEvent toEvent(Claim claim, ClaimStatus from, UUID actorId, ClaimEventType type) {
        BigDecimal amount = claim.getApprovedAmount() != null ? claim.getApprovedAmount() : claim.getClaimedAmount();
        User adjuster = claim.getAssignedAdjuster();
        return new ClaimEvent(UUID.randomUUID(), type, ClaimEvent.CURRENT_SCHEMA_VERSION, clock.instant(),
                claim.getId(), claim.getClaimNumber(), claim.getClaimant().getId(),
                adjuster == null ? null : adjuster.getId(),
                from == null ? null : from.name(), claim.getStatus().name(), amount, actorId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
