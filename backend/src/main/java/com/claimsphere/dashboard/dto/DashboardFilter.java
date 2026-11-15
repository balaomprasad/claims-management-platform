package com.claimsphere.dashboard.dto;

import com.claimsphere.common.domain.CurrencyCode;
import com.claimsphere.common.domain.Market;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Optional dashboard filters. Dates filter on the claim submission date (UTC).
 *
 * @param ageingDays threshold for the "pending beyond threshold" metric; defaults to 30
 */
public record DashboardFilter(
        Market market,
        CurrencyCode currency,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate submittedTo,
        Integer ageingDays) {

    public static final int DEFAULT_AGEING_DAYS = 30;

    public int ageingThresholdDays() {
        return ageingDays != null ? ageingDays : DEFAULT_AGEING_DAYS;
    }
}
