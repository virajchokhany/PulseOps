package io.pulseops.domain.incident;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.pulseops.domain.IncidentStatus;
import io.pulseops.domain.Severity;
import io.pulseops.domain.alert.AlertEvent;

/**
 * Published to the {@code incidents} topic by Incident Correlation and consumed independently by
 * the Incident Store and the AI Investigation Worker.
 *
 * <p>{@code incidentKey} is minted by the correlation engine rather than by the database, because
 * correlation has to name the incident in this event before any store has committed a row for it.
 *
 * <p>The event carries the full current state rather than a delta. Consumers can therefore apply it
 * without reading anything first, and a consumer that missed an earlier event still converges.
 *
 * @param version        monotonic per incident; lets consumers discard events that arrive out of order
 * @param triggeringAlert the alert that caused this create or update, so consumers need no extra lookup
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IncidentEvent(
        String eventId,
        IncidentEventType type,
        UUID incidentKey,
        Instant timestamp,
        String title,
        IncidentStatus status,
        Severity severity,
        String primaryService,
        List<String> affectedServices,
        int alertCount,
        long version,
        AlertEvent triggeringAlert,
        String correlationId) {
}
