package io.pulseops.api.incident;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.pulseops.domain.ConsumerGroups;
import io.pulseops.domain.CorrelationId;
import io.pulseops.domain.Topics;
import io.pulseops.domain.alert.AlertEvent;
import io.pulseops.domain.incident.IncidentEvent;
import io.pulseops.infrastructure.alert.AlertRepository;
import io.pulseops.infrastructure.idempotency.IdempotencyGuard;
import io.pulseops.infrastructure.incident.IncidentEntity;
import io.pulseops.infrastructure.incident.IncidentRepository;
import io.pulseops.infrastructure.incident.IncidentTimelineEntity;
import io.pulseops.infrastructure.incident.IncidentTimelineRepository;
import io.pulseops.infrastructure.kafka.EventPayloadCodec;
import io.pulseops.infrastructure.kafka.NonRetryableEventException;

import java.util.LinkedHashSet;

/**
 * Incident Store: the durable, queryable projection of incident state.
 *
 * <p>Reads the {@code incidents} topic in its own consumer group, alongside the AI Investigation
 * Worker. Neither waits for the other; correlation does not call this component synchronously.
 */
@Component
public class IncidentStoreConsumer {

    private static final Logger log = LoggerFactory.getLogger(IncidentStoreConsumer.class);

    private final EventPayloadCodec codec;
    private final IdempotencyGuard idempotencyGuard;
    private final IncidentRepository incidentRepository;
    private final IncidentTimelineRepository timelineRepository;
    private final AlertRepository alertRepository;

    public IncidentStoreConsumer(EventPayloadCodec codec,
                                 IdempotencyGuard idempotencyGuard,
                                 IncidentRepository incidentRepository,
                                 IncidentTimelineRepository timelineRepository,
                                 AlertRepository alertRepository) {
        this.codec = codec;
        this.idempotencyGuard = idempotencyGuard;
        this.incidentRepository = incidentRepository;
        this.timelineRepository = timelineRepository;
        this.alertRepository = alertRepository;
    }

    @KafkaListener(topics = Topics.INCIDENTS, groupId = ConsumerGroups.INCIDENT_STORE)
    @Transactional
    public void handle(ConsumerRecord<String, String> record) {
        EventPayloadCodec.adoptCorrelationId(record);
        try {
            IncidentEvent event = codec.decode(record.value(), IncidentEvent.class);
            validate(event);

            if (!idempotencyGuard.claim(ConsumerGroups.INCIDENT_STORE, event.eventId())) {
                log.debug("Incident event already applied, skipping eventId={}", event.eventId());
                return;
            }
            apply(event);
        } finally {
            CorrelationId.clear();
        }
    }

    private void apply(IncidentEvent event) {
        IncidentEntity incident = incidentRepository.findById(event.incidentKey()).orElse(null);

        if (incident == null) {
            // Also covers an UPDATED arriving without its CREATED: the event carries full state,
            // so the projection can be built from any single event rather than getting stuck.
            incident = new IncidentEntity(
                    event.incidentKey(), event.title(), event.severity(), event.primaryService(),
                    new LinkedHashSet<>(event.affectedServices()), event.correlationId(),
                    event.timestamp(), event.version());
            incidentRepository.save(incident);
            addTimelineEntry(event, "INCIDENT_OPENED",
                    "Incident opened from alert " + alertSummary(event.triggeringAlert()));
            log.info("Stored new incident key={} title='{}'", event.incidentKey(), event.title());
        } else if (event.version() <= incident.getVersion()) {
            // Correlation stamps a monotonic version. A lower one means a redelivered or
            // reordered event that would otherwise roll state backwards.
            log.debug("Ignoring stale incident event version={} (stored={})",
                    event.version(), incident.getVersion());
            return;
        } else {
            incident.apply(event.title(), event.severity(), event.primaryService(),
                    new LinkedHashSet<>(event.affectedServices()), event.alertCount(),
                    event.version(), event.timestamp());
            // rca_status is deliberately not touched here. The AI Investigation Worker owns that
            // field end to end; two consumer groups writing the same column would race.
            addTimelineEntry(event, "ALERT_CORRELATED",
                    "Correlated alert " + alertSummary(event.triggeringAlert()));
            log.info("Updated incident key={} version={} alerts={}",
                    event.incidentKey(), event.version(), event.alertCount());
        }

        linkAlert(event);
    }

    /**
     * Links the alert row to its incident. The row is expected to exist because the Alert Engine
     * persists before publishing, but a missing row is logged rather than thrown: losing a link is
     * not worth failing the projection over.
     */
    private void linkAlert(IncidentEvent event) {
        AlertEvent alert = event.triggeringAlert();
        if (alert == null || alert.alertId() == null) {
            return;
        }
        alertRepository.findById(alert.alertId()).ifPresentOrElse(
                stored -> stored.assignToIncident(event.incidentKey()),
                () -> log.warn("Alert {} not found while linking to incident {}",
                        alert.alertId(), event.incidentKey()));
    }

    private void addTimelineEntry(IncidentEvent event, String entryType, String summary) {
        timelineRepository.save(new IncidentTimelineEntity(
                event.incidentKey(), event.timestamp(), entryType, summary, event.correlationId()));
    }

    private static String alertSummary(AlertEvent alert) {
        if (alert == null) {
            return "(unknown)";
        }
        return "%s on %s (%s)".formatted(alert.alertType(), alert.service(), alert.severity());
    }

    private static void validate(IncidentEvent event) {
        if (event.eventId() == null || event.eventId().isBlank()) {
            throw new NonRetryableEventException("Incident event without eventId");
        }
        if (event.incidentKey() == null) {
            throw new NonRetryableEventException("Incident event " + event.eventId() + " has no incidentKey");
        }
        if (event.type() == null) {
            throw new NonRetryableEventException("Incident event " + event.eventId() + " has no type");
        }
        if (event.primaryService() == null || event.primaryService().isBlank()) {
            throw new NonRetryableEventException("Incident event " + event.eventId() + " has no primaryService");
        }
    }
}
