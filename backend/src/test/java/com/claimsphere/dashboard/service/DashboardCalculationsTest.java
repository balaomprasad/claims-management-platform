package com.claimsphere.dashboard.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardCalculationsTest {

    private static final Instant NOW = Instant.parse("2026-11-15T00:00:00Z");

    @Test
    void bucketsOpenClaimsByAgeAndCountsThoseBeyondThreshold() {
        var ageing = DashboardCalculations.ageing(List.of(daysAgo(0), daysAgo(7), daysAgo(8), daysAgo(31),
                daysAgo(45), daysAgo(120)), NOW, 30);

        assertThat(ageing.buckets()).extracting(b -> b.claims()).containsExactly(2L, 1L, 2L, 0L, 1L);
        assertThat(ageing.beyondThreshold()).isEqualTo(3);
    }

    @Test
    void averageProcessingTimeIsRoundedToOneDecimal() {
        var time = DashboardCalculations.processingTime(List.of(
                new Instant[]{daysAgo(10), daysAgo(8)},
                new Instant[]{daysAgo(10), NOW.minus(Duration.ofHours(36))}));

        assertThat(time.closedClaims()).isEqualTo(2);
        assertThat(time.averageDays()).isEqualTo(5.3);
    }

    @Test
    void processingTimeIsNullWhenNothingClosed() {
        assertThat(DashboardCalculations.processingTime(List.of()).averageDays()).isNull();
    }

    private static Instant daysAgo(int days) {
        return NOW.minus(Duration.ofDays(days));
    }
}
