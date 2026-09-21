package io.pulseops.domain.alert;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.pulseops.domain.Severity;

/**
 * A deterministic rule breach. Published to the {@code alerts} topic and consumed by Incident
 * Correlation.
 *
 * <p>The metric value and threshold travel with the alert so downstream consumers, and eventually
 * the LLM, can see <em>how far</em> past the line the service was, not merely that a line was crossed.
 *
 * @param windowSeconds width of the evaluation window
 * @param sampleCount   how many samples produced the value; distinguishes a real breach from a
 *                      statistical accident on a handful of requests
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlertEvent(
        String alertId,
        Instant timestamp,
        String service,
        String alertType,
        Severity severity,
        String description,
        AlertMetric metric,
        Double metricValue,
        Double threshold,
        Integer windowSeconds,
        Integer sampleCount,
        String correlationId) {
}
