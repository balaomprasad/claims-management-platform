package com.claimsphere.claim.domain;

import com.claimsphere.common.domain.CurrencyCode;
import com.claimsphere.common.domain.Market;
import com.claimsphere.common.exception.BusinessRuleException;
import com.claimsphere.common.exception.ConflictException;
import com.claimsphere.common.exception.InvalidStateTransitionException;
import com.claimsphere.policy.domain.Policy;
import com.claimsphere.policy.domain.ProductType;
import com.claimsphere.user.domain.Role;
import com.claimsphere.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaimTest {

    private Claim claim;
    private User officer;

    @BeforeEach
    void setUp() {
        User claimant = user(Role.CLAIMANT);
        officer = user(Role.CLAIMS_OFFICER);
        Policy policy = new Policy("POL-1", claimant, ProductType.HEALTH, Market.INDIA, CurrencyCode.INR,
                new BigDecimal("500000"), BigDecimal.ZERO, LocalDate.now().minusYears(1), LocalDate.now().plusYears(1));
        claim = new Claim("CLM-1", policy, claimant, LocalDate.now(), "Chennai", "Surgery",
                new BigDecimal("1000"), null);
    }

    @Test
    void copiesClassificationFromPolicy() {
        assertThat(claim.getClaimType()).isEqualTo(ProductType.HEALTH);
        assertThat(claim.getMarket()).isEqualTo(Market.INDIA);
        assertThat(claim.getCurrency()).isEqualTo(CurrencyCode.INR);
        assertThat(claim.getPriority()).isEqualTo(ClaimPriority.NORMAL);
    }

    @Test
    void informationRequestRoundTripReturnsToReview() {
        claim.assignTo(officer);
        claim.requestInformation();
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.INFO_REQUESTED);

        claim.provideInformation();
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
    }

    @Test
    void cannotAssignClaimThatIsAwaitingInformation() {
        claim.assignTo(officer);
        claim.requestInformation();

        assertThatThrownBy(() -> claim.assignTo(officer)).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void cannotProvideInformationUnlessRequested() {
        assertThatThrownBy(() -> claim.provideInformation()).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void settlementCannotExceedApprovedAmountAndClosesClaim() {
        claim.assignTo(officer);
        claim.approve(new BigDecimal("800"));

        assertThatThrownBy(() -> claim.settle(new BigDecimal("800.01"), LocalDate.now(), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("errorCode", "SETTLED_AMOUNT_EXCEEDS_APPROVED");

        claim.settle(new BigDecimal("800"), LocalDate.now(), "PAY-1");
        Instant now = Instant.now();
        claim.markClosed(now);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SETTLED);
        assertThat(claim.getSettledAmount()).isEqualByComparingTo("800.00");
        assertThat(claim.getClosedAt()).isEqualTo(now);
    }

    @Test
    void detailsAreLockedOnceReviewStarts() {
        claim.assignTo(officer);

        assertThatThrownBy(() -> claim.updateDetails("x", "y", BigDecimal.TEN))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", "CLAIM_NOT_EDITABLE");
    }

    @Test
    void reassignmentRequiresReviewStateAndDifferentOfficer() {
        assertThatThrownBy(() -> claim.reassignTo(officer))
                .hasFieldOrPropertyWithValue("errorCode", "CLAIM_NOT_REASSIGNABLE");
        claim.assignTo(officer);
        assertThatThrownBy(() -> claim.reassignTo(officer))
                .hasFieldOrPropertyWithValue("errorCode", "ALREADY_ASSIGNED_TO_OFFICER");
    }

    private static User user(Role role) {
        User user = new User(UUID.randomUUID() + "@example.com", "hash", role.name(), role);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
