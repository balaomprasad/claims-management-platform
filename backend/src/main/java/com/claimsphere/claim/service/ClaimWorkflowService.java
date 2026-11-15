package com.claimsphere.claim.service;

import com.claimsphere.audit.domain.AuditAction;
import com.claimsphere.audit.service.AuditService;
import com.claimsphere.claim.domain.Claim;
import com.claimsphere.claim.domain.ClaimAssessment;
import com.claimsphere.claim.domain.ClaimAssignment;
import com.claimsphere.claim.domain.ClaimDecision;
import com.claimsphere.claim.domain.ClaimInformationRequest;
import com.claimsphere.claim.domain.ClaimPriority;
import com.claimsphere.claim.domain.ClaimStatus;
import com.claimsphere.claim.dto.ApproveClaimRequest;
import com.claimsphere.claim.dto.AssessClaimRequest;
import com.claimsphere.claim.dto.AssignClaimRequest;
import com.claimsphere.claim.dto.ChangePriorityRequest;
import com.claimsphere.claim.dto.ClaimAssessmentResponse;
import com.claimsphere.claim.dto.ClaimResponse;
import com.claimsphere.claim.dto.RejectClaimRequest;
import com.claimsphere.claim.dto.RequestInformationRequest;
import com.claimsphere.claim.dto.SettleClaimRequest;
import com.claimsphere.claim.event.ClaimEventType;
import com.claimsphere.claim.repository.ClaimAssessmentRepository;
import com.claimsphere.claim.repository.ClaimAssignmentRepository;
import com.claimsphere.claim.repository.ClaimDecisionRepository;
import com.claimsphere.claim.repository.ClaimInformationRequestRepository;
import com.claimsphere.claim.repository.ClaimRepository;
import com.claimsphere.common.exception.BusinessRuleException;
import com.claimsphere.common.exception.ConflictException;
import com.claimsphere.security.CurrentUser;
import com.claimsphere.user.domain.Role;
import com.claimsphere.user.domain.User;
import com.claimsphere.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Claims-officer and manager operations: assignment, assessment, information requests, decisions,
 * settlement and prioritisation. Every status change is recorded via {@link ClaimTransitionRecorder}.
 */
@Service
@Transactional
public class ClaimWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(ClaimWorkflowService.class);

    private final ClaimRepository claimRepository;
    private final ClaimAssignmentRepository assignmentRepository;
    private final ClaimAssessmentRepository assessmentRepository;
    private final ClaimDecisionRepository decisionRepository;
    private final ClaimInformationRequestRepository informationRequestRepository;
    private final UserService userService;
    private final ClaimLookup claimLookup;
    private final ClaimAccessPolicy accessPolicy;
    private final ClaimTransitionRecorder recorder;
    private final AuditService auditService;
    private final Clock clock;

    public ClaimWorkflowService(ClaimRepository claimRepository, ClaimAssignmentRepository assignmentRepository,
                                ClaimAssessmentRepository assessmentRepository,
                                ClaimDecisionRepository decisionRepository,
                                ClaimInformationRequestRepository informationRequestRepository,
                                UserService userService, ClaimLookup claimLookup, ClaimAccessPolicy accessPolicy,
                                ClaimTransitionRecorder recorder, AuditService auditService, Clock clock) {
        this.claimRepository = claimRepository;
        this.assignmentRepository = assignmentRepository;
        this.assessmentRepository = assessmentRepository;
        this.decisionRepository = decisionRepository;
        this.informationRequestRepository = informationRequestRepository;
        this.userService = userService;
        this.claimLookup = claimLookup;
        this.accessPolicy = accessPolicy;
        this.recorder = recorder;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Officers self-assign unassigned claims. Managers and admins assign any officer and may reassign a claim
     * that is already in review. Conflicting concurrent assignments are rejected by optimistic locking.
     */
    public ClaimResponse assign(UUID claimId, AssignClaimRequest request, CurrentUser user) {
        Claim claim = claimLookup.loadVisible(claimId, user);
        User officer = userService.getUserWithRole(resolveOfficerId(request.officerId(), user), Role.CLAIMS_OFFICER);
        User actor = userService.getReference(user.id());

        ClaimStatus from = claim.getStatus();
        if (claim.getAssignedOfficer() == null) {
            claim.assignTo(officer);
        } else if (user.is(Role.CLAIMS_OFFICER)) {
            throw new ConflictException("CLAIM_ALREADY_ASSIGNED", "The claim is already assigned to an officer");
        } else {
            claim.reassignTo(officer);
        }
        assignmentRepository.save(new ClaimAssignment(claim, officer, actor, clock.instant()));
        recorder.record(claim, from, actor, request.comment(), ClaimEventType.CLAIM_ASSIGNED);
        log.info("Claim assigned claimId={} officerId={}", claimId, officer.getId());
        return ClaimResponse.from(claim);
    }

    public ClaimAssessmentResponse assess(UUID claimId, AssessClaimRequest request, CurrentUser user) {
        Claim claim = claimLookup.loadVisible(claimId, user);
        accessPolicy.requireAssignedOfficer(claim, user);
        if (!claim.getStatus().isInReview()) {
            throw new ConflictException("CLAIM_NOT_IN_REVIEW",
                    "Assessments can only be recorded while the claim is in review (status " + claim.getStatus() + ")");
        }
        if (request.recommendedAmount() != null
                && request.recommendedAmount().compareTo(claim.getEstimatedLoss()) > 0) {
            throw new BusinessRuleException("RECOMMENDED_AMOUNT_EXCEEDS_ESTIMATED_LOSS",
                    "Recommended amount cannot exceed the estimated loss of " + claim.getEstimatedLoss());
        }
        ClaimAssessment assessment = assessmentRepository.save(new ClaimAssessment(claim,
                userService.getReference(user.id()), request.notes().trim(), request.recommendedAmount(),
                clock.instant()));
        auditService.record(user.id(), AuditAction.CLAIM_ASSESSED, ClaimTransitionRecorder.ENTITY_TYPE, claimId,
                request.recommendedAmount() == null ? null : "Recommended " + request.recommendedAmount());
        log.info("Assessment recorded claimId={} assessmentId={}", claimId, assessment.getId());
        return ClaimAssessmentResponse.from(assessment);
    }

    public ClaimResponse requestInformation(UUID claimId, RequestInformationRequest request, CurrentUser user) {
        Claim claim = claimLookup.loadVisible(claimId, user);
        accessPolicy.requireAssignedOfficer(claim, user);
        User officer = userService.getReference(user.id());

        ClaimStatus from = claim.getStatus();
        claim.requestInformation();
        String message = request.message().trim();
        informationRequestRepository.save(new ClaimInformationRequest(claim, officer, message, clock.instant()));
        recorder.record(claim, from, officer, message, ClaimEventType.CLAIM_INFORMATION_REQUESTED);
        log.info("Information requested claimId={}", claimId);
        return ClaimResponse.from(claim);
    }

    public ClaimResponse approve(UUID claimId, ApproveClaimRequest request, CurrentUser user) {
        Claim claim = claimLookup.loadVisible(claimId, user);
        accessPolicy.requireAssignedOfficer(claim, user);
        User officer = userService.getReference(user.id());

        ClaimStatus from = claim.getStatus();
        claim.approve(request.approvedAmount());
        String comment = ClaimTransitionRecorder.blankToNull(request.comment());
        decisionRepository.save(ClaimDecision.approved(claim, claim.getApprovedAmount(), comment, officer,
                clock.instant()));
        recorder.record(claim, from, officer, comment, ClaimEventType.CLAIM_APPROVED);
        log.info("Claim approved claimId={} approvedAmount={} currency={}",
                claimId, claim.getApprovedAmount(), claim.getCurrency());
        return ClaimResponse.from(claim);
    }

    public ClaimResponse reject(UUID claimId, RejectClaimRequest request, CurrentUser user) {
        Claim claim = claimLookup.loadVisible(claimId, user);
        accessPolicy.requireAssignedOfficer(claim, user);
        User officer = userService.getReference(user.id());

        ClaimStatus from = claim.getStatus();
        String reason = request.reason().trim();
        claim.reject();
        decisionRepository.save(ClaimDecision.rejected(claim, reason, officer, clock.instant()));
        recorder.record(claim, from, officer, reason, ClaimEventType.CLAIM_REJECTED);
        log.info("Claim rejected claimId={}", claimId);
        return ClaimResponse.from(claim);
    }

    public ClaimResponse settle(UUID claimId, SettleClaimRequest request, CurrentUser user) {
        Claim claim = claimLookup.loadVisible(claimId, user);
        accessPolicy.requireAssignedOfficerOrManager(claim, user);
        LocalDate today = LocalDate.now(clock);
        LocalDate settlementDate = request.settlementDate() != null ? request.settlementDate() : today;
        if (settlementDate.isAfter(today)) {
            throw new BusinessRuleException("SETTLEMENT_DATE_IN_FUTURE", "Settlement date cannot be in the future");
        }

        ClaimStatus from = claim.getStatus();
        String reference = ClaimTransitionRecorder.blankToNull(request.settlementReference());
        claim.settle(request.settledAmount(), settlementDate, reference);
        String comment = "Settled %s %s on %s%s".formatted(claim.getSettledAmount(), claim.getCurrency(),
                settlementDate, reference == null ? "" : " (ref " + reference + ")");
        recorder.record(claim, from, userService.getReference(user.id()), comment, ClaimEventType.CLAIM_SETTLED);
        log.info("Claim settled claimId={} settledAmount={} currency={}",
                claimId, claim.getSettledAmount(), claim.getCurrency());
        return ClaimResponse.from(claim);
    }

    public ClaimResponse changePriority(UUID claimId, ChangePriorityRequest request, CurrentUser user) {
        Claim claim = claimLookup.loadVisible(claimId, user);
        ClaimPriority previous = claim.getPriority();
        claim.changePriority(request.priority());
        claimRepository.saveAndFlush(claim);
        auditService.record(user.id(), AuditAction.CLAIM_PRIORITY_CHANGED, ClaimTransitionRecorder.ENTITY_TYPE,
                claimId, previous + " -> " + request.priority());
        log.info("Claim priority changed claimId={} priority={}", claimId, request.priority());
        return ClaimResponse.from(claim);
    }

    private UUID resolveOfficerId(UUID requestedOfficerId, CurrentUser user) {
        if (user.is(Role.CLAIMS_OFFICER)) {
            if (requestedOfficerId != null && !requestedOfficerId.equals(user.id())) {
                throw new AccessDeniedException("Claims officers can only assign claims to themselves");
            }
            return user.id();
        }
        if (requestedOfficerId == null) {
            throw new BusinessRuleException("OFFICER_REQUIRED", "officerId is required");
        }
        return requestedOfficerId;
    }
}
