package io.pulseops.domain.telemetry;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * The single telemetry contract between ShopFlow and PulseOps.
 *
 * <p>Unknown fields are ignored so an emitting service can add a field before the platform knows
 * about it, which is the normal ordering when a team ships their own instrumentation.
 *
 * @param eventId           producer-generated id; the idempotency key for every consumer downstream
 * @param correlationId     carried across telemetry, alerts, incidents and investigation logs
 * @param deploymentVersion lets the AI worker line telemetry up against a deployment
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelemetryEvent(
        @NotBlank String eventId,
        @NotNull Instant timestamp,
        @NotBlank String service,
        @NotBlank String environment,
        @NotNull TelemetryType type,
        String endpoint,
        Integer statusCode,
        Integer latencyMs,
        String message,
        String traceId,
        String correlationId,
        String deploymentVersion) {

    /** True when this event represents a server-side failure, which is what the Alert Engine counts. */
    public boolean isServerError() {
        return statusCode != null && statusCode >= 500;
    }
}
