package com.claimsphere.claim.web;

import com.claimsphere.claim.dto.ApproveClaimRequest;
import com.claimsphere.claim.dto.AssessClaimRequest;
import com.claimsphere.claim.dto.AssignClaimRequest;
import com.claimsphere.claim.dto.ChangePriorityRequest;
import com.claimsphere.claim.dto.ClaimAssessmentResponse;
import com.claimsphere.claim.dto.ClaimResponse;
import com.claimsphere.claim.dto.RejectClaimRequest;
import com.claimsphere.claim.dto.RequestInformationRequest;
import com.claimsphere.claim.dto.SettleClaimRequest;
import com.claimsphere.claim.service.ClaimWorkflowService;
import com.claimsphere.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Claims-officer and manager workflow commands. Role checks here; assignment/ownership checks in the service.
 */
@RestController
@RequestMapping("/api/v1/claims/{claimId}")
public class ClaimWorkflowController {

    private final ClaimWorkflowService workflowService;
    private final CurrentUserProvider currentUserProvider;

    public ClaimWorkflowController(ClaimWorkflowService workflowService, CurrentUserProvider currentUserProvider) {
        this.workflowService = workflowService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/assign")
    @PreAuthorize("hasAnyRole('CLAIMS_OFFICER', 'MANAGER', 'ADMIN')")
    public ClaimResponse assign(@PathVariable UUID claimId,
                                @Valid @RequestBody(required = false) AssignClaimRequest request) {
        return workflowService.assign(claimId, request != null ? request : AssignClaimRequest.self(),
                currentUserProvider.get());
    }

    @PostMapping("/assessments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLAIMS_OFFICER')")
    public ClaimAssessmentResponse assess(@PathVariable UUID claimId, @Valid @RequestBody AssessClaimRequest request) {
        return workflowService.assess(claimId, request, currentUserProvider.get());
    }

    @PostMapping("/request-information")
    @PreAuthorize("hasRole('CLAIMS_OFFICER')")
    public ClaimResponse requestInformation(@PathVariable UUID claimId,
                                            @Valid @RequestBody RequestInformationRequest request) {
        return workflowService.requestInformation(claimId, request, currentUserProvider.get());
    }

    @PostMapping("/approve")
    @PreAuthorize("hasRole('CLAIMS_OFFICER')")
    public ClaimResponse approve(@PathVariable UUID claimId, @Valid @RequestBody ApproveClaimRequest request) {
        return workflowService.approve(claimId, request, currentUserProvider.get());
    }

    @PostMapping("/reject")
    @PreAuthorize("hasRole('CLAIMS_OFFICER')")
    public ClaimResponse reject(@PathVariable UUID claimId, @Valid @RequestBody RejectClaimRequest request) {
        return workflowService.reject(claimId, request, currentUserProvider.get());
    }

    @PostMapping("/settle")
    @PreAuthorize("hasAnyRole('CLAIMS_OFFICER', 'MANAGER')")
    public ClaimResponse settle(@PathVariable UUID claimId, @Valid @RequestBody SettleClaimRequest request) {
        return workflowService.settle(claimId, request, currentUserProvider.get());
    }

    @PutMapping("/priority")
    @PreAuthorize("hasAnyRole('CLAIMS_OFFICER', 'MANAGER')")
    public ClaimResponse changePriority(@PathVariable UUID claimId,
                                        @Valid @RequestBody ChangePriorityRequest request) {
        return workflowService.changePriority(claimId, request, currentUserProvider.get());
    }
}
