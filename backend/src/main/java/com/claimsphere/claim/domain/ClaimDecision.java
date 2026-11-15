package com.claimsphere.claim.domain;

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
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The single approve/reject decision recorded for a claim, with who decided and when.
 */
@Entity
@Immutable
@Table(name = "claim_decisions")
public class ClaimDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false, unique = true)
    private Claim claim;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private DecisionOutcome outcome;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 1000)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decided_by", nullable = false)
    private User decidedBy;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected ClaimDecision() {
    }

    private ClaimDecision(Claim claim, DecisionOutcome outcome, BigDecimal amount, String reason, User decidedBy,
                          Instant decidedAt) {
        this.claim = claim;
        this.outcome = outcome;
        this.amount = amount;
        this.reason = reason;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
    }

    public static ClaimDecision approved(Claim claim, BigDecimal amount, String comment, User decidedBy, Instant at) {
        return new ClaimDecision(claim, DecisionOutcome.APPROVED, amount, comment, decidedBy, at);
    }

    public static ClaimDecision rejected(Claim claim, String reason, User decidedBy, Instant at) {
        return new ClaimDecision(claim, DecisionOutcome.REJECTED, null, reason, decidedBy, at);
    }

    public UUID getId() {
        return id;
    }

    public DecisionOutcome getOutcome() {
        return outcome;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public User getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
