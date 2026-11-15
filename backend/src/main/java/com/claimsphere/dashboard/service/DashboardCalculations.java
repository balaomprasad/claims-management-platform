package com.claimsphere.dashboard.service;

import com.claimsphere.dashboard.dto.ManagerDashboardResponse.Ageing;
import com.claimsphere.dashboard.dto.ManagerDashboardResponse.AgeingBucket;
import com.claimsphere.dashboard.dto.ManagerDashboardResponse.ProcessingTime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure calculations behind the time-based dashboard metrics (kept separate so they are unit-testable).
 */
final class DashboardCalculations {

    private static final int[][] BUCKETS = {{0, 7}, {8, 30}, {31, 60}, {61, 90}};

    private DashboardCalculations() {
    }

    /** Buckets open claims by age in whole days since submission and counts those older than the threshold. */
    static Ageing ageing(List<Instant> openClaimCreatedAt, Instant now, int thresholdDays) {
        long[] counts = new long[BUCKETS.length + 1];
        long beyond = 0;
        for (Instant createdAt : openClaimCreatedAt) {
            long age = Duration.between(createdAt, now).toDays();
            counts[bucketIndex(age)]++;
            if (age > thresholdDays) {
                beyond++;
            }
        }
        List<AgeingBucket> buckets = new ArrayList<>();
        for (int i = 0; i < BUCKETS.length; i++) {
            buckets.add(new AgeingBucket(BUCKETS[i][0] + "-" + BUCKETS[i][1] + " days", counts[i]));
        }
        buckets.add(new AgeingBucket("90+ days", counts[BUCKETS.length]));
        return new Ageing(thresholdDays, beyond, buckets);
    }

    /** Average of (closedAt - createdAt) in days, rounded to one decimal; null when nothing has closed yet. */
    static ProcessingTime processingTime(List<Instant[]> createdAndClosed) {
        if (createdAndClosed.isEmpty()) {
            return new ProcessingTime(null, 0);
        }
        long totalSeconds = 0;
        for (Instant[] pair : createdAndClosed) {
            totalSeconds += Duration.between(pair[0], pair[1]).toSeconds();
        }
        BigDecimal averageDays = BigDecimal.valueOf(totalSeconds)
                .divide(BigDecimal.valueOf(86_400L * createdAndClosed.size()), 1, RoundingMode.HALF_UP);
        return new ProcessingTime(averageDays.doubleValue(), createdAndClosed.size());
    }

    private static int bucketIndex(long ageDays) {
        for (int i = 0; i < BUCKETS.length; i++) {
            if (ageDays <= BUCKETS[i][1]) {
                return i;
            }
        }
        return BUCKETS.length;
    }
}
