package com.claimsphere.claim.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimStatusTest {

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @CsvSource({
            "SUBMITTED,      UNDER_REVIEW,   true",
            "SUBMITTED,      WITHDRAWN,      true",
            "SUBMITTED,      APPROVED,       false",
            "SUBMITTED,      SETTLED,        false",
            "SUBMITTED,      INFO_REQUESTED, false",
            "UNDER_REVIEW,   INFO_REQUESTED, true",
            "UNDER_REVIEW,   APPROVED,       true",
            "UNDER_REVIEW,   REJECTED,       true",
            "UNDER_REVIEW,   WITHDRAWN,      true",
            "UNDER_REVIEW,   SETTLED,        false",
            "INFO_REQUESTED, UNDER_REVIEW,   true",
            "INFO_REQUESTED, WITHDRAWN,      true",
            "INFO_REQUESTED, APPROVED,       false",
            "INFO_REQUESTED, REJECTED,       false",
            "APPROVED,       SETTLED,        true",
            "APPROVED,       REJECTED,       false",
            "APPROVED,       WITHDRAWN,      false",
            "REJECTED,       UNDER_REVIEW,   false",
            "SETTLED,        APPROVED,       false",
            "WITHDRAWN,      UNDER_REVIEW,   false"
    })
    void enforcesTransitionMatrix(ClaimStatus from, ClaimStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test
    void openStatusesExcludeTerminalStates() {
        assertThat(ClaimStatus.openStatuses()).containsExactlyInAnyOrder(
                ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW, ClaimStatus.INFO_REQUESTED, ClaimStatus.APPROVED);
    }
}
