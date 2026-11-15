package com.claimsphere.policy.service;

import com.claimsphere.audit.domain.AuditAction;
import com.claimsphere.audit.service.AuditService;
import com.claimsphere.common.domain.CurrencyCode;
import com.claimsphere.common.exception.BusinessRuleException;
import com.claimsphere.common.exception.ResourceNotFoundException;
import com.claimsphere.policy.domain.Policy;
import com.claimsphere.policy.dto.CreatePolicyRequest;
import com.claimsphere.policy.dto.PolicyResponse;
import com.claimsphere.policy.repository.PolicyRepository;
import com.claimsphere.security.CurrentUser;
import com.claimsphere.user.domain.Role;
import com.claimsphere.user.domain.User;
import com.claimsphere.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

@Service
public class PolicyService {

    private static final Logger log = LoggerFactory.getLogger(PolicyService.class);

    private final PolicyRepository policyRepository;
    private final UserService userService;
    private final AuditService auditService;

    public PolicyService(PolicyRepository policyRepository, UserService userService, AuditService auditService) {
        this.policyRepository = policyRepository;
        this.userService = userService;
        this.auditService = auditService;
    }

    @Transactional
    public PolicyResponse createPolicy(CreatePolicyRequest request) {
        if (!request.endDate().isAfter(request.startDate())) {
            throw new BusinessRuleException("INVALID_POLICY_PERIOD", "Policy end date must be after start date");
        }
        if (request.deductible().compareTo(request.coverageAmount()) >= 0) {
            throw new BusinessRuleException("INVALID_DEDUCTIBLE", "Deductible must be lower than coverage amount");
        }
        User holder = userService.getUserWithRole(request.holderId(), Role.CLAIMANT);
        CurrencyCode currency = request.currency() != null ? request.currency() : request.market().localCurrency();
        Policy policy = policyRepository.save(new Policy(generatePolicyNumber(), holder, request.productType(),
                request.market(), currency,
                request.coverageAmount().setScale(2, RoundingMode.HALF_UP),
                request.deductible().setScale(2, RoundingMode.HALF_UP),
                request.startDate(), request.endDate()));
        auditService.record(AuditAction.POLICY_CREATED, "POLICY", policy.getId(),
                "%s %s %s %s".formatted(policy.getPolicyNumber(), policy.getProductType(), policy.getMarket(),
                        policy.getCurrency()));
        log.info("Policy created policyId={} holderId={}", policy.getId(), holder.getId());
        return PolicyResponse.from(policy);
    }

    /**
     * Claimants see only their own policies; claims staff and admins see all.
     */
    @Transactional(readOnly = true)
    public Page<PolicyResponse> listPolicies(CurrentUser currentUser, Pageable pageable) {
        Page<Policy> policies = currentUser.is(Role.CLAIMANT)
                ? policyRepository.findByHolderId(currentUser.id(), pageable)
                : policyRepository.findAllBy(pageable);
        return policies.map(PolicyResponse::from);
    }

    @Transactional(readOnly = true)
    public PolicyResponse getPolicy(UUID policyId, CurrentUser currentUser) {
        return PolicyResponse.from(getVisiblePolicy(policyId, currentUser));
    }

    /**
     * Returns the policy if visible to the user. Policies owned by someone else are reported as not found.
     */
    @Transactional(readOnly = true)
    public Policy getVisiblePolicy(UUID policyId, CurrentUser currentUser) {
        Policy policy = policyRepository.findWithHolderById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy", policyId));
        if (currentUser.is(Role.CLAIMANT) && !policy.isHeldBy(currentUser.id())) {
            throw new ResourceNotFoundException("Policy", policyId);
        }
        return policy;
    }

    private static String generatePolicyNumber() {
        return "POL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
    }
}
