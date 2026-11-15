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

import java.time.Instant;
import java.util.UUID;

/**
 * An officer's request for additional information and the claimant's eventual response.
 */
@Entity
@Table(name = "claim_information_requests")
public class ClaimInformationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false, updatable = false)
    private Claim claim;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false, updatable = false)
    private User requestedBy;

    @Column(nullable = false, length = 2000, updatable = false)
    private String message;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(length = 4000)
    private String response;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responded_by")
    private User respondedBy;

    @Column(name = "responded_at")
    private Instant respondedAt;

    protected ClaimInformationRequest() {
    }

    public ClaimInformationRequest(Claim claim, User requestedBy, String message, Instant requestedAt) {
        this.claim = claim;
        this.requestedBy = requestedBy;
        this.message = message;
        this.requestedAt = requestedAt;
    }

    public void respond(String response, User respondedBy, Instant respondedAt) {
        this.response = response;
        this.respondedBy = respondedBy;
        this.respondedAt = respondedAt;
    }

    public UUID getId() {
        return id;
    }

    public User getRequestedBy() {
        return requestedBy;
    }

    public String getMessage() {
        return message;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public String getResponse() {
        return response;
    }

    public User getRespondedBy() {
        return respondedBy;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }
}
