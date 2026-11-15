package com.claimsphere.user.bootstrap;

import com.claimsphere.policy.domain.ProductType;
import com.claimsphere.policy.dto.CreatePolicyRequest;
import com.claimsphere.policy.service.PolicyService;
import com.claimsphere.user.domain.Role;
import com.claimsphere.user.domain.User;
import com.claimsphere.user.repository.UserRepository;
import com.claimsphere.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Seeds demo users and policies when the {@code demo} profile is active. Passwords come from the
 * {@code DEMO_*_PASSWORD} environment variables; nothing is seeded unless all three are provided.
 * Idempotent: existing demo users are left untouched.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    static final String ADMIN_EMAIL = "admin@claimsphere.local";
    static final String ADJUSTER_EMAIL = "adjuster@claimsphere.local";
    static final String CLAIMANT_EMAIL = "claimant@claimsphere.local";

    private final UserService userService;
    private final UserRepository userRepository;
    private final PolicyService policyService;
    private final Clock clock;
    private final String adminPassword;
    private final String adjusterPassword;
    private final String claimantPassword;

    public DemoDataSeeder(UserService userService, UserRepository userRepository, PolicyService policyService,
                          Clock clock,
                          @Value("${app.demo.admin-password}") String adminPassword,
                          @Value("${app.demo.adjuster-password}") String adjusterPassword,
                          @Value("${app.demo.claimant-password}") String claimantPassword) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.policyService = policyService;
        this.clock = clock;
        this.adminPassword = adminPassword;
        this.adjusterPassword = adjusterPassword;
        this.claimantPassword = claimantPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminPassword.isBlank() || adjusterPassword.isBlank() || claimantPassword.isBlank()) {
            log.warn("Demo profile active but DEMO_*_PASSWORD variables are not all set; skipping demo data");
            return;
        }
        if (userRepository.existsByEmail(CLAIMANT_EMAIL)) {
            log.info("Demo data already present; skipping");
            return;
        }
        userService.createUser(ADMIN_EMAIL, adminPassword, "Alex Admin", Role.ADMIN);
        userService.createUser(ADJUSTER_EMAIL, adjusterPassword, "Jordan Adjuster", Role.ADJUSTER);
        User claimant = userService.createUser(CLAIMANT_EMAIL, claimantPassword, "Casey Claimant", Role.CLAIMANT);

        LocalDate today = LocalDate.now(clock);
        policyService.createPolicy(new CreatePolicyRequest(claimant.getId(), ProductType.MOTOR,
                new BigDecimal("25000.00"), new BigDecimal("500.00"), today.minusMonths(3), today.plusMonths(9)));
        policyService.createPolicy(new CreatePolicyRequest(claimant.getId(), ProductType.TRAVEL,
                new BigDecimal("10000.00"), new BigDecimal("100.00"), today.minusMonths(1), today.plusMonths(11)));
        log.info("Demo data seeded: 3 users, 2 policies");
    }
}
