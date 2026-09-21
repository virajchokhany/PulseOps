package io.pulseops.domain;

import java.util.UUID;

import org.slf4j.MDC;

/**
 * Correlation id plumbing, shared by everything that emits or consumes PulseOps events.
 *
 * <p>The same id travels as an HTTP header between services and as a Kafka record header through
 * the telemetry, alert and incident topics, so a single customer request can be followed from
 * "order failed" all the way to the RCA that explains it.
 */
public final class CorrelationId {

    /** Header name on both HTTP requests and Kafka records. */
    public static final String HEADER = "X-Correlation-Id";

    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static String currentOrNew() {
        String existing = current();
        return existing != null && !existing.isBlank() ? existing : UUID.randomUUID().toString();
    }

    public static void set(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(MDC_KEY, correlationId);
        }
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
