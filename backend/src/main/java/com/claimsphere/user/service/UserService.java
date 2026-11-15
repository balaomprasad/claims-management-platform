package com.claimsphere.user.service;

import com.claimsphere.audit.domain.AuditAction;
import com.claimsphere.audit.service.AuditService;
import com.claimsphere.common.domain.Market;
import com.claimsphere.common.exception.BusinessRuleException;
import com.claimsphere.common.exception.ConflictException;
import com.claimsphere.common.exception.ResourceNotFoundException;
import com.claimsphere.user.domain.ClaimantProfile;
import com.claimsphere.user.domain.Role;
import com.claimsphere.user.domain.User;
import com.claimsphere.user.dto.ClaimantProfileRequest;
import com.claimsphere.user.dto.ClaimantProfileResponse;
import com.claimsphere.user.dto.CreateUserRequest;
import com.claimsphere.user.dto.UserResponse;
import com.claimsphere.user.repository.ClaimantProfileRepository;
import com.claimsphere.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * User provisioning and lookup. Also used by other modules to resolve users by id and expected role.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final ClaimantProfileRepository claimantProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository userRepository, ClaimantProfileRepository claimantProfileRepository,
                       PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.claimantProfileRepository = claimantProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    /**
     * Admin provisioning entry point: claimants also get a profile (market required); staff do not.
     */
    @Transactional
    public User provision(CreateUserRequest request) {
        if (request.role() == Role.CLAIMANT) {
            if (request.market() == null) {
                throw new BusinessRuleException("MARKET_REQUIRED", "market is required when creating a claimant");
            }
            return createClaimant(request.email(), request.password(), request.fullName(), request.market(), null);
        }
        return createUser(request.email(), request.password(), request.fullName(), request.role());
    }

    @Transactional
    public User createClaimant(String email, String rawPassword, String fullName, Market market, String phone) {
        User user = createUser(email, rawPassword, fullName, Role.CLAIMANT);
        claimantProfileRepository.save(new ClaimantProfile(user.getId(), market, blankToNull(phone), null));
        return user;
    }

    @Transactional
    public User createUser(String email, String rawPassword, String fullName, Role role) {
        String normalisedEmail = normaliseEmail(email);
        if (userRepository.existsByEmail(normalisedEmail)) {
            throw new ConflictException("EMAIL_ALREADY_REGISTERED", "An account with this email already exists");
        }
        User user = userRepository.save(
                new User(normalisedEmail, passwordEncoder.encode(rawPassword), fullName.trim(), role));
        auditService.record(AuditAction.USER_CREATED, "USER", user.getId(), "role " + role);
        log.info("User created userId={} role={}", user.getId(), role);
        return user;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(Role role, Pageable pageable) {
        Page<User> users = role == null ? userRepository.findAll(pageable) : userRepository.findByRole(role, pageable);
        return users.map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public User getUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    /**
     * Returns a lazy reference without loading the row; use only for ids already proven to exist (e.g. from a JWT).
     */
    public User getReference(UUID userId) {
        return userRepository.getReferenceById(userId);
    }

    /**
     * Loads a user and verifies they hold the expected role, e.g. when assigning a claim to a claims officer.
     */
    @Transactional(readOnly = true)
    public User getUserWithRole(UUID userId, Role expectedRole) {
        User user = getUser(userId);
        if (!user.hasRole(expectedRole)) {
            throw new BusinessRuleException("INVALID_USER_ROLE",
                    "User %s does not have role %s".formatted(userId, expectedRole));
        }
        return user;
    }

    static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public ClaimantProfileResponse getClaimantProfile(UUID userId) {
        return ClaimantProfileResponse.from(getUser(userId), loadProfile(userId));
    }

    @Transactional
    public ClaimantProfileResponse updateClaimantProfile(UUID userId, ClaimantProfileRequest request) {
        ClaimantProfile profile = loadProfile(userId);
        profile.update(request.market(), blankToNull(request.phone()), blankToNull(request.address()));
        auditService.record(userId, AuditAction.CLAIMANT_PROFILE_UPDATED, "USER", userId, null);
        return ClaimantProfileResponse.from(getUser(userId), profile);
    }

    private ClaimantProfile loadProfile(UUID userId) {
        return claimantProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Claimant profile", userId));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
