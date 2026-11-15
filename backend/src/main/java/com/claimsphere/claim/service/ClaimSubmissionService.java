package com.claimsphere.claim.service;

import com.claimsphere.audit.domain.AuditAction;
import com.claimsphere.audit.service.AuditService;
import com.claimsphere.claim.domain.Claim;
import com.claimsphere.claim.domain.ClaimStatus;
import com.claimsphere.claim.dto.ClaimActionRequest;
import com.claimsphere.claim.dto.ClaimResponse;
import com.claimsphere.claim.dto.ClaimSubmissionResult;
import com.claimsphere.claim.dto.CreateClaimRequest;
import com.claimsphere.claim.dto.ProvideInformationRequest;
import com.claimsphere.claim.dto.UpdateClaimRequest;
import com.claimsphere.claim.event.ClaimEventType;
import com.claimsphere.claim.repository.ClaimInformationRequestRepository;
import com.claimsphere.claim.repository.ClaimRepository;
import com.claimsphere.common.exception.BusinessRuleException;
import com.claimsphere.policy.domain.Policy;
import com.claimsphere.policy.service.PolicyService;
import com.claimsphere.security.CurrentUser;
import com.claimsphere.user.domain.User;
import com.claimsphere.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Claimant-side operations: submit, correct details, respond to information requests and withdraw.
 */
@Service
@Transactional
public class ClaimSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(ClaimSubmissionService.class);

    private final ClaimRepository claimRepository;
    private final ClaimInformationRequestRepository informationRequestRepository;
    private final PolicyService policyService;
    private final UserService userService;
    private final ClaimLookup claimLookup;
    private final ClaimAccessPolicy accessPolicy;
    private final ClaimNumberGenerator claimNumberGenerator;
    private final ClaimTransitionRecorder recorder;
    private final AuditService auditService;
    private final Clock clock;

    public ClaimSubmissionService(ClaimRepository claimRepository,
                                  ClaimInformationRequestRepository informationRequestRepository,
                                  PolicyService policyService, UserService userService, ClaimLookup claimLookup,
                                  ClaimAccessPolicy accessPolicy, ClaimNumberGenerator claimNumberGenerator,
                                  ClaimTransitionRecorder recorder, AuditService auditService, Clock clock) {
        this.claimRepository = claimRepository;
        this.informationRequestRepository = informationRequestRepository;
        this.policyService = policyService;
        this.userService = userService;
        this.claimLookup = claimLookup;
        this.accessPolicy = accessPolicy;
        this.claimNumberGenerator = claimNumberGenerator;
        this.recorder = recorder;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Submits a claim. When an idempotency key is supplied and a claim was already created with it by the same
     * claimant, that claim is returned instead of creating a duplicate.
     */
    public ClaimSubmissionResult submit(CreateClaimRequest request, String idempotencyKey, CurrentUser user) {
        if (idempotencyKey != null) {
            Optional<Claim> existing = claimRepository.findByClaimantIdAndSubmissionKey(user.id(), idempotencyKey);
            if (existing.isPresent()) {
                log.info("Duplicate submission ignored claimId={}", existing.get().getId());
                return new ClaimSubmissionResult(ClaimResponse.from(existing.get()), false);
            }
        }
        Policy policy = policyService.getVisiblePolicy(request.policyId(), user);
        if (!policy.isHeldBy(user.id())) {
            throw new AccessDeniedException("Claims can only be filed against your own policy");
        }
        validateAgainstPolicy(policy, request.incidentDate(), request.estimatedLoss());

        User claimant = userService.getReference(user.id());
        Claim claim = new Claim(claimNumberGenerator.next(), policy, claimant, request.incidentDate(),
                request.incidentLocation().trim(), request.incidentDescription().trim(), request.estimatedLoss(),
                idempotencyKey);
        recorder.record(claim, null, claimant, null, ClaimEventType.CLAIM_SUBMITTED);
        log.info("Claim submitted claimId={} claimNumber={} policyId={} market={}",
                claim.getId(), claim.getClaimNumber(), policy.getId(), claim.getMarket());
        return new ClaimSubmissionResult(ClaimResponse.from(claim), true);
    }

    public ClaimResponse updateDetails(UUID claimId, UpdateClaimRequest request, CurrentUser user) {
        Claim claim = claimLookup.loadVisible(claimId, user);
        accessPolicy.requireOwner(claim, user);
        if (request.estimatedLoss().compareTo(claim.getPolicy().getCoverageAmount()) > 0) {
            throw amountExceedsCoverage(claim.getPolicy());
        }
        claim.updateDetails(request.incidentLocation().trim(), request.incidentDescription().trim(),
                request.estimatedLoss());
        claimRepository.saveAndFlush(claim);
        auditService.record(user.id(), AuditAction.CLAIM_UPDATED, ClaimTransitionRecorder.ENTITY_TYPE, claimId,
                "Estimated loss " + claim.getEstimatedLoss());
        log.info("Claim details updated claimId={}", claimId);
        return ClaimResponse.from(claim);
    }

    public ClaimResponse provideInformation(UUID claimId, ProvideInformationRequest request, CurrentUser user) {
        Claim claim = claimLookup.loadVisible(claimId, user);
        accessPolicy.requireOwner(claim, user);
        User claimant = userService.getReference(user.id());

        ClaimStatus from = claim.getStatus();
        claim.provideInformation();
        String response = request.response().trim();
        informationRequestRepository.findFirstByClaimIdAndRespondedAtIsNullOrderByRequestedAtDesc(claimId)
                .ifPresent(open -> open.respond(response, claimant, clock.instant()));
        recorder.record(claim, from, claimant, response, ClaimEventType.CLAIM_INFORMATION_PROVIDED);
        log.info("Information provided claimId={}", claimId);
        return ClaimResponse.from(claim);
    }

    public ClaimResponse withdraw(UUID claimId, ClaimActionRequest request, CurrentUser user) {
        Claim claim = claimLookup.loadVisible(claimId, user);
        accessPolicy.requireOwner(claim, user);

        ClaimStatus from = claim.getStatus();
        claim.withdraw();
        recorder.record(claim, from, userService.getReference(user.id()), request.comment(),
                ClaimEventType.CLAIM_WITHDRAWN);
        log.info("Claim withdrawn claimId={}", claimId);
        return ClaimResponse.from(claim);
    }

    private void validateAgainstPolicy(Policy policy, LocalDate incidentDate, BigDecimal estimatedLoss) {
        if (!policy.isActive()) {
            throw new BusinessRuleException("POLICY_NOT_ACTIVE", "Claims can only be filed against an active policy");
        }
        if (incidentDate.isAfter(LocalDate.now(clock))) {
            throw new BusinessRuleException("INCIDENT_DATE_IN_FUTURE", "Incident date cannot be in the future");
        }
        if (!policy.covers(incidentDate)) {
            throw new BusinessRuleException("INCIDENT_OUTSIDE_POLICY_PERIOD",
                    "Incident date must fall within the policy period %s to %s"
                            .formatted(policy.getStartDate(), policy.getEndDate()));
        }
        if (estimatedLoss.compareTo(policy.getCoverageAmount()) > 0) {
            throw amountExceedsCoverage(policy);
        }
    }

    private static BusinessRuleException amountExceedsCoverage(Policy policy) {
        return new BusinessRuleException("AMOUNT_EXCEEDS_COVERAGE",
                "Estimated loss exceeds the policy coverage of %s %s"
                        .formatted(policy.getCoverageAmount(), policy.getCurrency()));
    }
}
