package com.claimsphere.claim.domain;

import com.claimsphere.common.domain.CurrencyCode;
import com.claimsphere.common.domain.Market;
import com.claimsphere.common.exception.BusinessRuleException;
import com.claimsphere.common.exception.ConflictException;
import com.claimsphere.common.exception.InvalidStateTransitionException;
import com.claimsphere.common.persistence.AuditableEntity;
import com.claimsphere.policy.domain.Policy;
import com.claimsphere.policy.domain.ProductType;
import com.claimsphere.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

/**
 * Insurance claim aggregate. State changes go through intention-revealing methods that enforce
 * the {@link ClaimStatus} transition rules and amount invariants. Authorisation is handled by the service layer.
 * <p>
 * Claim type, market and currency are copied from the policy at submission so that the claim keeps the terms it
 * was filed under and reporting queries do not need to join policies.
 */
@Entity
@Table(name = "claims")
public class Claim extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "claim_number", nullable = false, unique = true, updatable = false)
    private String claimNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false, updatable = false)
    private Policy policy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claimant_id", nullable = false, updatable = false)
    private User claimant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_officer_id")
    private User assignedOfficer;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "claim_type", nullable = false, updatable = false)
    private ProductType claimType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false)
    private Market market;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false, length = 3)
    private CurrencyCode currency;

    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(name = "incident_location", length = 300)
    private String incidentLocation;

    @Column(name = "incident_description", nullable = false, length = 2000)
    private String incidentDescription;

    @Column(name = "estimated_loss", nullable = false, precision = 15, scale = 2)
    private BigDecimal estimatedLoss;

    @Column(name = "approved_amount", precision = 15, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "settled_amount", precision = 15, scale = 2)
    private BigDecimal settledAmount;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    @Column(name = "settlement_reference", length = 100)
    private String settlementReference;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private ClaimPriority priority;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private ClaimStatus status;

    @Column(name = "submission_key", length = 64, updatable = false)
    private String submissionKey;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    private Long version;

    protected Claim() {
    }

    public Claim(String claimNumber, Policy policy, User claimant, LocalDate incidentDate, String incidentLocation,
                 String incidentDescription, BigDecimal estimatedLoss, String submissionKey) {
        this.claimNumber = claimNumber;
        this.policy = policy;
        this.claimant = claimant;
        this.claimType = policy.getProductType();
        this.market = policy.getMarket();
        this.currency = policy.getCurrency();
        this.incidentDate = incidentDate;
        this.incidentLocation = incidentLocation;
        this.incidentDescription = incidentDescription;
        this.estimatedLoss = money(estimatedLoss);
        this.submissionKey = submissionKey;
        this.priority = ClaimPriority.NORMAL;
        this.status = ClaimStatus.SUBMITTED;
    }

    /** First assignment: starts the review. */
    public void assignTo(User officer) {
        transitionTo(ClaimStatus.UNDER_REVIEW, ClaimStatus.SUBMITTED);
        this.assignedOfficer = officer;
    }

    /** Hands a claim that is already in review to another officer without changing its status. */
    public void reassignTo(User officer) {
        if (!status.isInReview()) {
            throw new ConflictException("CLAIM_NOT_REASSIGNABLE",
                    "Only claims under review or awaiting information can be reassigned (status " + status + ")");
        }
        if (isAssignedTo(officer.getId())) {
            throw new ConflictException("ALREADY_ASSIGNED_TO_OFFICER", "The claim is already assigned to this officer");
        }
        this.assignedOfficer = officer;
    }

    public void requestInformation() {
        transitionTo(ClaimStatus.INFO_REQUESTED);
    }

    public void provideInformation() {
        transitionTo(ClaimStatus.UNDER_REVIEW, ClaimStatus.INFO_REQUESTED);
    }

    public void approve(BigDecimal amount) {
        ensureCanTransition(ClaimStatus.APPROVED);
        BigDecimal scaled = money(amount);
        if (scaled.compareTo(estimatedLoss) > 0) {
            throw new BusinessRuleException("APPROVED_AMOUNT_EXCEEDS_ESTIMATED_LOSS",
                    "Approved amount cannot exceed the estimated loss of " + estimatedLoss);
        }
        this.status = ClaimStatus.APPROVED;
        this.approvedAmount = scaled;
    }

    public void reject() {
        transitionTo(ClaimStatus.REJECTED);
    }

    public void settle(BigDecimal amount, LocalDate date, String reference) {
        ensureCanTransition(ClaimStatus.SETTLED);
        BigDecimal scaled = money(amount);
        if (scaled.compareTo(approvedAmount) > 0) {
            throw new BusinessRuleException("SETTLED_AMOUNT_EXCEEDS_APPROVED",
                    "Settled amount cannot exceed the approved amount of " + approvedAmount);
        }
        this.status = ClaimStatus.SETTLED;
        this.settledAmount = scaled;
        this.settlementDate = date;
        this.settlementReference = reference;
    }

    public void withdraw() {
        transitionTo(ClaimStatus.WITHDRAWN);
    }

    public void changePriority(ClaimPriority newPriority) {
        if (status.isTerminal()) {
            throw new ConflictException("CLAIM_CLOSED", "Priority cannot be changed on a closed claim");
        }
        this.priority = newPriority;
    }

    /** Claimants may correct details until an officer starts reviewing, or while information is requested. */
    public void updateDetails(String incidentLocation, String incidentDescription, BigDecimal estimatedLoss) {
        if (status != ClaimStatus.SUBMITTED && status != ClaimStatus.INFO_REQUESTED) {
            throw new ConflictException("CLAIM_NOT_EDITABLE",
                    "Claim details can only be changed while SUBMITTED or INFO_REQUESTED (status " + status + ")");
        }
        this.incidentLocation = incidentLocation;
        this.incidentDescription = incidentDescription;
        this.estimatedLoss = money(estimatedLoss);
    }

    public void markClosed(Instant at) {
        if (status.isTerminal() && closedAt == null) {
            this.closedAt = at;
        }
    }

    public boolean isOwnedBy(UUID userId) {
        return claimant.getId().equals(userId);
    }

    public boolean isAssignedTo(UUID userId) {
        return assignedOfficer != null && assignedOfficer.getId().equals(userId);
    }

    private void transitionTo(ClaimStatus target, ClaimStatus... requiredCurrent) {
        ensureCanTransition(target, requiredCurrent);
        this.status = target;
    }

    private void ensureCanTransition(ClaimStatus target, ClaimStatus... requiredCurrent) {
        boolean currentAllowed = requiredCurrent.length == 0 || Arrays.asList(requiredCurrent).contains(status);
        if (!currentAllowed || !status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException(status, target);
        }
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() {
        return id;
    }

    public String getClaimNumber() {
        return claimNumber;
    }

    public Policy getPolicy() {
        return policy;
    }

    public User getClaimant() {
        return claimant;
    }

    public User getAssignedOfficer() {
        return assignedOfficer;
    }

    public ProductType getClaimType() {
        return claimType;
    }

    public Market getMarket() {
        return market;
    }

    public CurrencyCode getCurrency() {
        return currency;
    }

    public LocalDate getIncidentDate() {
        return incidentDate;
    }

    public String getIncidentLocation() {
        return incidentLocation;
    }

    public String getIncidentDescription() {
        return incidentDescription;
    }

    public BigDecimal getEstimatedLoss() {
        return estimatedLoss;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public BigDecimal getSettledAmount() {
        return settledAmount;
    }

    public LocalDate getSettlementDate() {
        return settlementDate;
    }

    public String getSettlementReference() {
        return settlementReference;
    }

    public ClaimPriority getPriority() {
        return priority;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public String getSubmissionKey() {
        return submissionKey;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Long getVersion() {
        return version;
    }
}
