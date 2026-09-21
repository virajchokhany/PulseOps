package io.pulseops.api.incident;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.pulseops.domain.IncidentStatus;
import io.pulseops.domain.RcaStatus;
import io.pulseops.domain.Severity;
import io.pulseops.infrastructure.alert.AlertEntity;
import io.pulseops.infrastructure.incident.IncidentEntity;
import io.pulseops.infrastructure.incident.IncidentTimelineEntity;

/** Read models returned to the Angular dashboard. */
public final class IncidentViews {

    private IncidentViews() {
    }

    public record IncidentSummary(
            UUID incidentKey,
            Long incidentNumber,
            String title,
            IncidentStatus status,
            Severity severity,
            String primaryService,
            Set<String> affectedServices,
            int alertCount,
            RcaStatus rcaStatus,
            Instant openedAt,
            Instant updatedAt) {

        public static IncidentSummary from(IncidentEntity incident) {
            return new IncidentSummary(
                    incident.getIncidentKey(), incident.getIncidentNumber(), incident.getTitle(),
                    incident.getStatus(), incident.getSeverity(), incident.getPrimaryService(),
                    incident.getAffectedServices(), incident.getAlertCount(), incident.getRcaStatus(),
                    incident.getOpenedAt(), incident.getUpdatedAt());
        }
    }

    public record AlertView(
            String alertId,
            String service,
            String alertType,
            String severity,
            String description,
            Double metricValue,
            Double threshold,
            Integer sampleCount,
            Instant occurredAt,
            UUID incidentKey) {

        public static AlertView from(AlertEntity alert) {
            return new AlertView(
                    alert.getAlertId(), alert.getService(), alert.getAlertType(), alert.getSeverity(),
                    alert.getDescription(), alert.getMetricValue(), alert.getThreshold(),
                    alert.getSampleCount(), alert.getOccurredAt(), alert.getIncidentKey());
        }
    }

    public record TimelineEntry(Instant occurredAt, String entryType, String summary, String correlationId) {

        public static TimelineEntry from(IncidentTimelineEntity entity) {
            return new TimelineEntry(entity.getOccurredAt(), entity.getEntryType(),
                    entity.getSummary(), entity.getCorrelationId());
        }
    }

    public record IncidentDetail(
            IncidentSummary incident,
            List<AlertView> alerts,
            List<TimelineEntry> timeline) {
    }
}
