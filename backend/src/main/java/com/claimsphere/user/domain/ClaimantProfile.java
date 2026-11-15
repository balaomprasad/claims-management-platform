package com.claimsphere.user.domain;

import com.claimsphere.common.domain.Market;
import com.claimsphere.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Claimant-specific details, keyed by the claimant's user id (1:1 with a CLAIMANT {@link User}).
 */
@Entity
@Table(name = "claimant_profiles")
public class ClaimantProfile extends AuditableEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private Market market;

    @Column(length = 300)
    private String address;

    protected ClaimantProfile() {
    }

    public ClaimantProfile(UUID userId, Market market, String phone, String address) {
        this.userId = userId;
        update(market, phone, address);
    }

    public void update(Market market, String phone, String address) {
        this.market = market;
        this.phone = phone;
        this.address = address;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPhone() {
        return phone;
    }

    public Market getMarket() {
        return market;
    }

    public String getAddress() {
        return address;
    }
}
