package com.claimsphere.user.service;

import com.claimsphere.audit.domain.AuditAction;
import com.claimsphere.audit.service.AuditService;
import com.claimsphere.security.JwtTokenService;
import com.claimsphere.user.domain.User;
import com.claimsphere.user.dto.LoginRequest;
import com.claimsphere.user.dto.LoginResponse;
import com.claimsphere.user.dto.RegisterRequest;
import com.claimsphere.user.dto.UserResponse;
import com.claimsphere.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Self-registration and credential-based login.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuditService auditService;
    private final String dummyPasswordHash;

    public AuthService(UserService userService, UserRepository userRepository,
                       PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService,
                       AuditService auditService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.auditService = auditService;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Registers a new claimant. The role is fixed server-side; callers cannot choose it.
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        User user = userService.createClaimant(request.email(), request.password(), request.fullName(),
                request.market(), request.phone());
        return UserResponse.from(user);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmail(UserService.normaliseEmail(request.email()));
        // Always run a hash comparison so response timing does not reveal whether the email exists.
        String hash = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !passwordMatches) {
            log.warn("Failed login attempt");
            throw new BadCredentialsException("Invalid email or password");
        }
        User authenticated = user.get();
        auditService.record(authenticated.getId(), AuditAction.USER_LOGIN, "USER", authenticated.getId(), null);
        log.info("User logged in userId={}", authenticated.getId());
        return LoginResponse.bearer(jwtTokenService.issueToken(authenticated),
                jwtTokenService.expiresInSeconds(), UserResponse.from(authenticated));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID userId) {
        return UserResponse.from(userService.getUser(userId));
    }
}
