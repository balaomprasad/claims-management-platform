package com.claimsphere.claim.web;

import com.claimsphere.claim.dto.ClaimActionRequest;
import com.claimsphere.claim.dto.ClaimAssessmentResponse;
import com.claimsphere.claim.dto.ClaimAssignmentResponse;
import com.claimsphere.claim.dto.ClaimDecisionResponse;
import com.claimsphere.claim.dto.ClaimHistoryResponse;
import com.claimsphere.claim.dto.ClaimInformationRequestResponse;
import com.claimsphere.claim.dto.ClaimResponse;
import com.claimsphere.claim.dto.ClaimSearchRequest;
import com.claimsphere.claim.dto.ClaimSubmissionResult;
import com.claimsphere.claim.dto.ClaimSummaryResponse;
import com.claimsphere.claim.dto.CreateClaimRequest;
import com.claimsphere.claim.dto.ProvideInformationRequest;
import com.claimsphere.claim.dto.UpdateClaimRequest;
import com.claimsphere.claim.service.ClaimQueryService;
import com.claimsphere.claim.service.ClaimSubmissionService;
import com.claimsphere.common.web.PageResponse;
import com.claimsphere.security.CurrentUserProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Claim queries (role-scoped) and claimant actions.
 */
@RestController
@RequestMapping("/api/v1/claims")
public class ClaimController {

    static final String STAFF = "hasAnyRole('CLAIMS_OFFICER', 'MANAGER', 'ADMIN')";

    private final ClaimQueryService queryService;
    private final ClaimSubmissionService submissionService;
    private final CurrentUserProvider currentUserProvider;

    public ClaimController(ClaimQueryService queryService, ClaimSubmissionService submissionService,
                           CurrentUserProvider currentUserProvider) {
        this.queryService = queryService;
        this.submissionService = submissionService;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Submits a claim. Clients should send a unique {@code Idempotency-Key} per logical submission; retries with
     * the same key return the original claim (200) instead of creating a duplicate (201).
     */
    @PostMapping
    @PreAuthorize("hasRole('CLAIMANT')")
    public ResponseEntity<ClaimResponse> submitClaim(
            @RequestHeader(name = "Idempotency-Key", required = false)
            @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$", message = "must be 8-64 characters: letters, digits, - or _")
            String idempotencyKey,
            @Valid @RequestBody CreateClaimRequest request) {
        ClaimSubmissionResult result = submissionService.submit(request, idempotencyKey, currentUserProvider.get());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.claim());
    }

    @GetMapping
    public PageResponse<ClaimResponse> searchClaims(ClaimSearchRequest search,
                                                    @PageableDefault(size = 20, sort = "createdAt",
                                                            direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(queryService.search(currentUserProvider.get(), search, pageable));
    }

    @GetMapping("/summary")
    @PreAuthorize(STAFF)
    public ClaimSummaryResponse getSummary() {
        return queryService.getSummary();
    }

    @GetMapping("/by-number/{claimNumber}")
    public ClaimResponse getClaimByNumber(@PathVariable String claimNumber) {
        return queryService.getClaimByNumber(claimNumber, currentUserProvider.get());
    }

    @GetMapping("/{claimId}")
    public ClaimResponse getClaim(@PathVariable UUID claimId) {
        return queryService.getClaim(claimId, currentUserProvider.get());
    }

    @PutMapping("/{claimId}")
    @PreAuthorize("hasRole('CLAIMANT')")
    public ClaimResponse updateClaim(@PathVariable UUID claimId, @Valid @RequestBody UpdateClaimRequest request) {
        return submissionService.updateDetails(claimId, request, currentUserProvider.get());
    }

    @GetMapping("/{claimId}/history")
    public List<ClaimHistoryResponse> getHistory(@PathVariable UUID claimId) {
        return queryService.getHistory(claimId, currentUserProvider.get());
    }

    @GetMapping("/{claimId}/decision")
    public ClaimDecisionResponse getDecision(@PathVariable UUID claimId) {
        return queryService.getDecision(claimId, currentUserProvider.get());
    }

    @GetMapping("/{claimId}/information-requests")
    public List<ClaimInformationRequestResponse> getInformationRequests(@PathVariable UUID claimId) {
        return queryService.getInformationRequests(claimId, currentUserProvider.get());
    }

    /** Internal assessment notes are visible to staff only. */
    @GetMapping("/{claimId}/assessments")
    @PreAuthorize(STAFF)
    public List<ClaimAssessmentResponse> getAssessments(@PathVariable UUID claimId) {
        return queryService.getAssessments(claimId, currentUserProvider.get());
    }

    @GetMapping("/{claimId}/assignments")
    @PreAuthorize(STAFF)
    public List<ClaimAssignmentResponse> getAssignments(@PathVariable UUID claimId) {
        return queryService.getAssignments(claimId, currentUserProvider.get());
    }

    @PostMapping("/{claimId}/information")
    @PreAuthorize("hasRole('CLAIMANT')")
    public ClaimResponse provideInformation(@PathVariable UUID claimId,
                                            @Valid @RequestBody ProvideInformationRequest request) {
        return submissionService.provideInformation(claimId, request, currentUserProvider.get());
    }

    @PostMapping("/{claimId}/withdraw")
    @PreAuthorize("hasRole('CLAIMANT')")
    public ClaimResponse withdrawClaim(@PathVariable UUID claimId,
                                       @Valid @RequestBody(required = false) ClaimActionRequest request) {
        return submissionService.withdraw(claimId, request != null ? request : ClaimActionRequest.empty(),
                currentUserProvider.get());
    }
}
