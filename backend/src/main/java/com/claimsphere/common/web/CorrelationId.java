package com.claimsphere.common.web;

import org.slf4j.MDC;

/**
 * Correlation-id conventions shared by HTTP, logging, the outbox and Kafka.
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }

    /** The correlation id of the current request/message, or {@code null} outside one. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
