package com.claimsphere.claim.service;

import com.claimsphere.claim.domain.ClaimStatus;
import com.claimsphere.claim.dto.ClaimAssessmentResponse;
import com.claimsphere.claim.dto.ClaimAssignmentResponse;
import com.claimsphere.claim.dto.ClaimDecisionResponse;
import com.claimsphere.claim.dto.ClaimHistoryResponse;
import com.claimsphere.claim.dto.ClaimInformationRequestResponse;
import com.claimsphere.claim.dto.ClaimResponse;
import com.claimsphere.claim.dto.ClaimSearchCriteria;
import com.claimsphere.claim.dto.ClaimSearchRequest;
import com.claimsphere.claim.dto.ClaimSummaryResponse;
import com.claimsphere.claim.repository.ClaimAssessmentRepository;
import com.claimsphere.claim.repository.ClaimAssignmentRepository;
import com.claimsphere.claim.repository.ClaimDecisionRepository;
import com.claimsphere.claim.repository.ClaimInformationRequestRepository;
import com.claimsphere.claim.repository.ClaimRepository;
import com.claimsphere.claim.repository.ClaimSpecifications;
import com.claimsphere.claim.repository.ClaimStatusHistoryRepository;
import com.claimsphere.common.exception.ResourceNotFoundException;
import com.claimsphere.security.CurrentUser;
import com.claimsphere.user.domain.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Read-side claim operations, scoped by the caller's role and ownership.
 */
@Service
@Transactional(readOnly = true)
public class ClaimQueryService {

    private final ClaimRepository claimRepository;
    private final ClaimStatusHistoryRepository historyRepository;
    private final ClaimAssessmentRepository assessmentRepository;
    private final ClaimDecisionRepository decisionRepository;
    private final ClaimInformationRequestRepository informationRequestRepository;
    private final ClaimAssignmentRepository assignmentRepository;
    private final ClaimLookup claimLookup;

    public ClaimQueryService(ClaimRepository claimRepository, ClaimStatusHistoryRepository historyRepository,
                             ClaimAssessmentRepository assessmentRepository, ClaimDecisionRepository decisionRepository,
                             ClaimInformationRequestRepository informationRequestRepository,
                             ClaimAssignmentRepository assignmentRepository, ClaimLookup claimLookup) {
        this.claimRepository = claimRepository;
        this.historyRepository = historyRepository;
        this.assessmentRepository = assessmentRepository;
        this.decisionRepository = decisionRepository;
        this.informationRequestRepository = informationRequestRepository;
        this.assignmentRepository = assignmentRepository;
        this.claimLookup = claimLookup;
    }

    public Page<ClaimResponse> search(CurrentUser user, ClaimSearchRequest request, Pageable pageable) {
        return claimRepository.findAll(ClaimSpecifications.matching(toCriteria(user, request)), pageable)
                .map(ClaimResponse::from);
    }

    public ClaimResponse getClaim(UUID claimId, CurrentUser user) {
        return ClaimResponse.from(claimLookup.loadVisible(claimId, user));
    }

    public ClaimResponse getClaimByNumber(String claimNumber, CurrentUser user) {
        return ClaimResponse.from(claimLookup.loadVisibleByNumber(claimNumber, user));
    }

    public List<ClaimHistoryResponse> getHistory(UUID claimId, CurrentUser user) {
        claimLookup.loadVisible(claimId, user);
        return historyRepository.findByClaimIdOrderByChangedAtAsc(claimId).stream()
                .map(ClaimHistoryResponse::from).toList();
    }

    public List<ClaimAssessmentResponse> getAssessments(UUID claimId, CurrentUser user) {
        claimLookup.loadVisible(claimId, user);
        return assessmentRepository.findByClaimIdOrderByCreatedAtAsc(claimId).stream()
                .map(ClaimAssessmentResponse::from).toList();
    }

    public List<ClaimAssignmentResponse> getAssignments(UUID claimId, CurrentUser user) {
        claimLookup.loadVisible(claimId, user);
        return assignmentRepository.findByClaimIdOrderByAssignedAtAsc(claimId).stream()
                .map(ClaimAssignmentResponse::from).toList();
    }

    public ClaimDecisionResponse getDecision(UUID claimId, CurrentUser user) {
        claimLookup.loadVisible(claimId, user);
        return decisionRepository.findByClaimId(claimId).map(ClaimDecisionResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Decision for claim", claimId));
    }

    public List<ClaimInformationRequestResponse> getInformationRequests(UUID claimId, CurrentUser user) {
        claimLookup.loadVisible(claimId, user);
        return informationRequestRepository.findByClaimIdOrderByRequestedAtAsc(claimId).stream()
                .map(ClaimInformationRequestResponse::from).toList();
    }

    public ClaimSummaryResponse getSummary() {
        Map<ClaimStatus, Long> counts = new EnumMap<>(ClaimStatus.class);
        for (ClaimStatus status : ClaimStatus.values()) {
            counts.put(status, 0L);
        }
        claimRepository.countByStatus().forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new ClaimSummaryResponse(counts, total);
    }

    /**
     * Applies role scoping: claimants are always restricted to their own claims, regardless of the filters sent.
     */
    private static ClaimSearchCriteria toCriteria(CurrentUser user, ClaimSearchRequest request) {
        UUID claimantId = user.is(Role.CLAIMANT) ? user.id() : null;
        UUID officerId = Boolean.TRUE.equals(request.assignedToMe()) ? user.id() : request.assignedOfficerId();
        Set<ClaimStatus> statuses = request.status() != null ? Set.of(request.status())
                : Boolean.TRUE.equals(request.openOnly()) ? ClaimStatus.openStatuses() : null;
        return new ClaimSearchCriteria(claimantId, statuses, request.market(), request.claimType(),
                request.priority(), request.currency(), officerId, Boolean.TRUE.equals(request.unassigned()),
                request.incidentFrom(), request.incidentTo(), request.submittedFrom(), request.submittedTo());
    }
}
