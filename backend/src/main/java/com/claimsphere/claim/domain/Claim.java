package com.claimsphere.claim.domain;

import com.claimsphere.common.exception.BusinessRuleException;
import com.claimsphere.common.exception.InvalidStateTransitionException;
import com.claimsphere.common.persistence.AuditableEntity;
import com.claimsphere.policy.domain.Policy;
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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Insurance claim aggregate. State changes go through intention-revealing methods that enforce
 * the {@link ClaimStatus} transition rules and amount invariants. Authorisation is handled by the service layer.
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
    @JoinColumn(name = "assigned_adjuster_id")
    private User assignedAdjuster;

    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "claimed_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal claimedAmount;

    @Column(name = "approved_amount", precision = 15, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private ClaimStatus status;

    @Version
    private Long version;

    protected Claim() {
    }

    public Claim(String claimNumber, Policy policy, User claimant, LocalDate incidentDate,
                 String description, BigDecimal claimedAmount) {
        this.claimNumber = claimNumber;
        this.policy = policy;
        this.claimant = claimant;
        this.incidentDate = incidentDate;
        this.description = description;
        this.claimedAmount = claimedAmount.setScale(2, RoundingMode.HALF_UP);
        this.status = ClaimStatus.SUBMITTED;
    }

    public void assignTo(User adjuster) {
        changeStatus(ClaimStatus.UNDER_REVIEW);
        this.assignedAdjuster = adjuster;
    }

    public void approve(BigDecimal amount) {
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        if (scaled.compareTo(claimedAmount) > 0) {
            throw new BusinessRuleException("APPROVED_AMOUNT_EXCEEDS_CLAIMED",
                    "Approved amount cannot exceed the claimed amount of " + claimedAmount);
        }
        changeStatus(ClaimStatus.APPROVED);
        this.approvedAmount = scaled;
    }

    public void reject(String reason) {
        changeStatus(ClaimStatus.REJECTED);
        this.rejectionReason = reason;
    }

    public void settle() {
        changeStatus(ClaimStatus.SETTLED);
    }

    public void withdraw() {
        changeStatus(ClaimStatus.WITHDRAWN);
    }

    public boolean isOwnedBy(UUID userId) {
        return claimant.getId().equals(userId);
    }

    public boolean isAssignedTo(UUID userId) {
        return assignedAdjuster != null && assignedAdjuster.getId().equals(userId);
    }

    private void changeStatus(ClaimStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException(status, target);
        }
        this.status = target;
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

    public User getAssignedAdjuster() {
        return assignedAdjuster;
    }

    public LocalDate getIncidentDate() {
        return incidentDate;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getClaimedAmount() {
        return claimedAmount;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }
}
