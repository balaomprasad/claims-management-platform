package com.claimsphere.policy.web;

import com.claimsphere.common.web.PageResponse;
import com.claimsphere.policy.dto.CreatePolicyRequest;
import com.claimsphere.policy.dto.PolicyResponse;
import com.claimsphere.policy.service.PolicyService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

    private final PolicyService policyService;
    private final CurrentUserProvider currentUserProvider;

    public PolicyController(PolicyService policyService, CurrentUserProvider currentUserProvider) {
        this.policyService = policyService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public PageResponse<PolicyResponse> listPolicies(@PageableDefault(size = 20, sort = "createdAt",
            direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(policyService.listPolicies(currentUserProvider.get(), pageable));
    }

    @GetMapping("/{policyId}")
    public PolicyResponse getPolicy(@PathVariable UUID policyId) {
        return policyService.getPolicy(policyId, currentUserProvider.get());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public PolicyResponse createPolicy(@Valid @RequestBody CreatePolicyRequest request) {
        return policyService.createPolicy(request);
    }
}
