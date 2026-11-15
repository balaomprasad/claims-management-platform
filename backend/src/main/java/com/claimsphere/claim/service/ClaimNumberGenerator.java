package com.claimsphere.claim.service;

import com.claimsphere.claim.repository.ClaimRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Year;

/**
 * Generates human-readable, unique claim numbers such as {@code CLM-2026-000042} from a database sequence.
 */
@Component
public class ClaimNumberGenerator {

    private final ClaimRepository claimRepository;
    private final Clock clock;

    public ClaimNumberGenerator(ClaimRepository claimRepository, Clock clock) {
        this.claimRepository = claimRepository;
        this.clock = clock;
    }

    public String next() {
        return "CLM-%d-%06d".formatted(Year.now(clock).getValue(), claimRepository.nextClaimSequence());
    }
}
