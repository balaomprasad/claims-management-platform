package com.claimsphere.claim.domain;

import com.claimsphere.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An officer's assessment note on a claim. Internal to staff; never shown to claimants.
 */
@Entity
@Immutable
@Table(name = "claim_assessments")
public class ClaimAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "officer_id", nullable = false)
    private User officer;

    @Column(nullable = false, length = 4000)
    private String notes;

    @Column(name = "recommended_amount", precision = 15, scale = 2)
    private BigDecimal recommendedAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ClaimAssessment() {
    }

    public ClaimAssessment(Claim claim, User officer, String notes, BigDecimal recommendedAmount, Instant createdAt) {
        this.claim = claim;
        this.officer = officer;
        this.notes = notes;
        this.recommendedAmount = recommendedAmount;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public User getOfficer() {
        return officer;
    }

    public String getNotes() {
        return notes;
    }

    public BigDecimal getRecommendedAmount() {
        return recommendedAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
