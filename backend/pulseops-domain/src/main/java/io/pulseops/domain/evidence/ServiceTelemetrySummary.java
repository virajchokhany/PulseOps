package io.pulseops.domain.evidence;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Aggregated telemetry for one service and one event type over the incident window.
 *
 * <p>Aggregates rather than raw events: a few thousand individual rows would dominate the prompt
 * and tell the model less than the rates and percentiles computed from them.
 *
 * <p>Split by event type on purpose. Inbound HTTP describes what callers experienced; DEPENDENCY
 * describes what this service experienced calling something else. Merging them would both distort
 * the error rate and erase the distinction between "this service is broken" and "the thing it calls
 * is broken" — which is the whole question during an incident.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ServiceTelemetrySummary(
        String service,
        String eventType,
        List<String> endpoints,
        Instant from,
        Instant to,
        int sampleCount,
        int errorCount,
        double errorRate,
        double avgLatencyMs,
        double p95LatencyMs,
        Map<String, Integer> statusCodeCounts,
        List<String> sampleErrorMessages,
        List<String> deploymentVersionsObserved) {
}
