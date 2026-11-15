package com.claimsphere.bootstrap;

import com.claimsphere.claim.domain.ClaimPriority;
import com.claimsphere.claim.dto.ApproveClaimRequest;
import com.claimsphere.claim.dto.AssignClaimRequest;
import com.claimsphere.claim.dto.ChangePriorityRequest;
import com.claimsphere.claim.dto.CreateClaimRequest;
import com.claimsphere.claim.dto.RequestInformationRequest;
import com.claimsphere.claim.dto.SettleClaimRequest;
import com.claimsphere.claim.service.ClaimSubmissionService;
import com.claimsphere.claim.service.ClaimWorkflowService;
import com.claimsphere.common.domain.Market;
import com.claimsphere.policy.domain.ProductType;
import com.claimsphere.policy.dto.CreatePolicyRequest;
import com.claimsphere.policy.dto.PolicyResponse;
import com.claimsphere.policy.service.PolicyService;
import com.claimsphere.security.CurrentUser;
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
import java.util.UUID;

/**
 * Seeds demo users, policies in several markets/currencies and claims in different lifecycle states when the
 * {@code demo} profile is active, so every portal (claimant, officer, manager) has meaningful data.
 * The shared password comes from {@code DEMO_PASSWORD}; nothing is seeded when it is blank. Idempotent.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String DOMAIN = "@claimsphere.local";

    private final UserService userService;
    private final UserRepository userRepository;
    private final PolicyService policyService;
    private final ClaimSubmissionService submissionService;
    private final ClaimWorkflowService workflowService;
    private final Clock clock;
    private final String password;

    public DemoDataSeeder(UserService userService, UserRepository userRepository, PolicyService policyService,
                          ClaimSubmissionService submissionService, ClaimWorkflowService workflowService,
                          Clock clock, @Value("${app.demo.password}") String password) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.policyService = policyService;
        this.submissionService = submissionService;
        this.workflowService = workflowService;
        this.clock = clock;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password.isBlank()) {
            log.warn("Demo profile active but DEMO_PASSWORD is not set; skipping demo data");
            return;
        }
        if (userRepository.existsByEmail("admin" + DOMAIN)) {
            log.info("Demo data already present; skipping");
            return;
        }
        userService.createUser("admin" + DOMAIN, password, "Alex Admin", Role.ADMIN);
        User manager = userService.createUser("manager" + DOMAIN, password, "Morgan Manager", Role.MANAGER);
        User jordan = userService.createUser("officer" + DOMAIN, password, "Jordan Officer", Role.CLAIMS_OFFICER);
        User priya = userService.createUser("officer2" + DOMAIN, password, "Priya Officer", Role.CLAIMS_OFFICER);
        User casey = userService.createClaimant("claimant" + DOMAIN, password, "Casey Tan", Market.SINGAPORE,
                "+65 6123 4567");
        User arjun = userService.createClaimant("claimant2" + DOMAIN, password, "Arjun Mehta", Market.INDIA, null);
        User mia = userService.createClaimant("claimant3" + DOMAIN, password, "Mia Wilson", Market.AUSTRALIA, null);

        LocalDate today = LocalDate.now(clock);
        PolicyResponse caseyMotor = policy(casey, ProductType.MOTOR, Market.SINGAPORE, "25000", "500", today);
        PolicyResponse caseyTravel = policy(casey, ProductType.TRAVEL, Market.SINGAPORE, "10000", "100", today);
        PolicyResponse arjunHealth = policy(arjun, ProductType.HEALTH, Market.INDIA, "500000", "5000", today);
        PolicyResponse miaProperty = policy(mia, ProductType.PROPERTY, Market.AUSTRALIA, "300000", "1000", today);

        // 1. New, unassigned claim in the officer queue.
        claim(casey, caseyMotor, today.minusDays(2), "Orchard Road, Singapore",
                "Rear-ended at a traffic light; bumper and tail lamp damaged.", "1850.75");

        // 2. Under review, high priority.
        UUID arjunClaim = claim(arjun, arjunHealth, today.minusDays(20), "Apollo Hospital, Chennai",
                "Emergency appendectomy and three days of hospitalisation.", "185000");
        workflowService.assign(arjunClaim, AssignClaimRequest.self(), principal(jordan));
        workflowService.changePriority(arjunClaim, new ChangePriorityRequest(ClaimPriority.HIGH), principal(jordan));

        // 3. Awaiting additional information from the claimant.
        UUID arjunSecond = claim(arjun, arjunHealth, today.minusDays(40), "Chennai",
                "Outpatient physiotherapy sessions after a sports injury.", "42000");
        workflowService.assign(arjunSecond, AssignClaimRequest.self(), principal(priya));
        workflowService.requestInformation(arjunSecond,
                new RequestInformationRequest("Please provide the physiotherapist's referral letter."),
                principal(priya));

        // 4. Approved, awaiting settlement (approved liability).
        UUID miaClaim = claim(mia, miaProperty, today.minusDays(35), "Bondi, NSW",
                "Storm damage to roof and ceiling; water ingress in two bedrooms.", "52000");
        workflowService.assign(miaClaim, new AssignClaimRequest(priya.getId(), "Property specialist"),
                principal(manager));
        workflowService.approve(miaClaim, new ApproveClaimRequest(new BigDecimal("45000"),
                "Roof repair covered; contents excluded"), principal(priya));

        // 5. Settled.
        UUID caseyTravelClaim = claim(casey, caseyTravel, today.minusDays(60), "Narita Airport, Tokyo",
                "Checked baggage lost on a connecting flight.", "2100");
        workflowService.assign(caseyTravelClaim, AssignClaimRequest.self(), principal(jordan));
        workflowService.approve(caseyTravelClaim, new ApproveClaimRequest(new BigDecimal("1800"),
                "Depreciation applied to electronics"), principal(jordan));
        workflowService.settle(caseyTravelClaim, new SettleClaimRequest(new BigDecimal("1800"), today.minusDays(5),
                "PAY-SG-0001"), principal(jordan));

        log.info("Demo data seeded: 7 users, 4 policies, 5 claims");
    }

    private PolicyResponse policy(User holder, ProductType type, Market market, String coverage, String deductible,
                                  LocalDate today) {
        return policyService.createPolicy(new CreatePolicyRequest(holder.getId(), type, market, null,
                new BigDecimal(coverage), new BigDecimal(deductible), today.minusMonths(6), today.plusMonths(6)));
    }

    private UUID claim(User claimant, PolicyResponse policy, LocalDate incidentDate, String location,
                       String description, String estimatedLoss) {
        return submissionService.submit(new CreateClaimRequest(policy.id(), incidentDate, location, description,
                new BigDecimal(estimatedLoss)), null, principal(claimant)).claim().id();
    }

    private static CurrentUser principal(User user) {
        return new CurrentUser(user.getId(), user.getEmail(), user.getRole());
    }
}
