package com.claimsphere.claim.web;

import com.claimsphere.claim.domain.ClaimStatus;
import com.claimsphere.claim.dto.ApproveClaimRequest;
import com.claimsphere.claim.dto.AssignClaimRequest;
import com.claimsphere.claim.dto.ClaimActionRequest;
import com.claimsphere.claim.dto.ClaimHistoryResponse;
import com.claimsphere.claim.dto.ClaimResponse;
import com.claimsphere.claim.dto.ClaimSummaryResponse;
import com.claimsphere.claim.dto.CreateClaimRequest;
import com.claimsphere.claim.dto.RejectClaimRequest;
import com.claimsphere.claim.service.ClaimService;
import com.claimsphere.claim.service.ClaimWorkflowService;
import com.claimsphere.common.web.PageResponse;
import com.claimsphere.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/claims")
public class ClaimController {

    private final ClaimService claimService;
    private final ClaimWorkflowService workflowService;
    private final CurrentUserProvider currentUserProvider;

    public ClaimController(ClaimService claimService, ClaimWorkflowService workflowService,
                           CurrentUserProvider currentUserProvider) {
        this.claimService = claimService;
        this.workflowService = workflowService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLAIMANT')")
    public ClaimResponse submitClaim(@Valid @RequestBody CreateClaimRequest request) {
        return workflowService.submit(request, currentUserProvider.get());
    }

    @GetMapping
    public PageResponse<ClaimResponse> listClaims(@RequestParam(required = false) ClaimStatus status,
                                                  @PageableDefault(size = 20, sort = "createdAt",
                                                          direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(claimService.listClaims(currentUserProvider.get(), status, pageable));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADJUSTER', 'ADMIN')")
    public ClaimSummaryResponse getSummary() {
        return claimService.getSummary();
    }

    @GetMapping("/{claimId}")
    public ClaimResponse getClaim(@PathVariable UUID claimId) {
        return claimService.getClaim(claimId, currentUserProvider.get());
    }

    @GetMapping("/{claimId}/history")
    public List<ClaimHistoryResponse> getHistory(@PathVariable UUID claimId) {
        return claimService.getHistory(claimId, currentUserProvider.get());
    }

    @PostMapping("/{claimId}/assign")
    @PreAuthorize("hasAnyRole('ADJUSTER', 'ADMIN')")
    public ClaimResponse assignClaim(@PathVariable UUID claimId,
                                     @Valid @RequestBody(required = false) AssignClaimRequest request) {
        AssignClaimRequest body = request != null ? request : new AssignClaimRequest(null, null);
        return workflowService.assign(claimId, body, currentUserProvider.get());
    }

    @PostMapping("/{claimId}/approve")
    @PreAuthorize("hasRole('ADJUSTER')")
    public ClaimResponse approveClaim(@PathVariable UUID claimId, @Valid @RequestBody ApproveClaimRequest request) {
        return workflowService.approve(claimId, request, currentUserProvider.get());
    }

    @PostMapping("/{claimId}/reject")
    @PreAuthorize("hasRole('ADJUSTER')")
    public ClaimResponse rejectClaim(@PathVariable UUID claimId, @Valid @RequestBody RejectClaimRequest request) {
        return workflowService.reject(claimId, request, currentUserProvider.get());
    }

    @PostMapping("/{claimId}/settle")
    @PreAuthorize("hasRole('ADMIN')")
    public ClaimResponse settleClaim(@PathVariable UUID claimId,
                                     @Valid @RequestBody(required = false) ClaimActionRequest request) {
        return workflowService.settle(claimId, orEmpty(request), currentUserProvider.get());
    }

    @PostMapping("/{claimId}/withdraw")
    @PreAuthorize("hasRole('CLAIMANT')")
    public ClaimResponse withdrawClaim(@PathVariable UUID claimId,
                                       @Valid @RequestBody(required = false) ClaimActionRequest request) {
        return workflowService.withdraw(claimId, orEmpty(request), currentUserProvider.get());
    }

    private static ClaimActionRequest orEmpty(ClaimActionRequest request) {
        return request != null ? request : ClaimActionRequest.empty();
    }
}
