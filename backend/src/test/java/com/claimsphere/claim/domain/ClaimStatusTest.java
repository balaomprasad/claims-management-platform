package com.claimsphere.claim.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimStatusTest {

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @CsvSource({
            "SUBMITTED,    UNDER_REVIEW, true",
            "SUBMITTED,    WITHDRAWN,    true",
            "SUBMITTED,    APPROVED,     false",
            "SUBMITTED,    SETTLED,      false",
            "UNDER_REVIEW, APPROVED,     true",
            "UNDER_REVIEW, REJECTED,     true",
            "UNDER_REVIEW, WITHDRAWN,    true",
            "UNDER_REVIEW, SETTLED,      false",
            "APPROVED,     SETTLED,      true",
            "APPROVED,     REJECTED,     false",
            "APPROVED,     WITHDRAWN,    false",
            "REJECTED,     UNDER_REVIEW, false",
            "SETTLED,      APPROVED,     false",
            "WITHDRAWN,    UNDER_REVIEW, false"
    })
    void enforcesTransitionMatrix(ClaimStatus from, ClaimStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @ParameterizedTest
    @CsvSource({"REJECTED, true", "SETTLED, true", "WITHDRAWN, true", "SUBMITTED, false", "APPROVED, false"})
    void identifiesTerminalStates(ClaimStatus status, boolean terminal) {
        assertThat(status.isTerminal()).isEqualTo(terminal);
    }
}
