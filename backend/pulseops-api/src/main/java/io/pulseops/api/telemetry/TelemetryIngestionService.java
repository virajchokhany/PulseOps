package io.pulseops.api.telemetry;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.pulseops.domain.CorrelationId;
import io.pulseops.domain.Topics;
import io.pulseops.domain.telemetry.TelemetryEvent;
import io.pulseops.infrastructure.kafka.EventPublisher;

/**
 * Telemetry Ingestion.
 *
 * <p>Its entire job is to validate an event, make sure it carries an id and a correlation id, and
 * put it on the {@code telemetry} topic. It deliberately does not store telemetry, does not
 * evaluate alert rules, does not create incidents and never calls the LLM. Keeping the write path
 * this thin is what lets ingestion stay fast and lets every downstream concern scale and fail
 * independently.
 */
@Service
public class TelemetryIngestionService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryIngestionService.class);

    private final EventPublisher eventPublisher;

    public TelemetryIngestionService(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public TelemetryEvent ingest(TelemetryEvent incoming) {
        TelemetryEvent event = normalise(incoming);

        // Keyed by service so one service's events stay ordered on a single partition, which is
        // what the Alert Engine's per-service sliding window relies on.
        eventPublisher.publish(Topics.TELEMETRY, event.service(), event);

        log.debug("Ingested telemetry eventId={} service={} endpoint={} status={} latencyMs={}",
                event.eventId(), event.service(), event.endpoint(), event.statusCode(), event.latencyMs());
        return event;
    }

    private TelemetryEvent normalise(TelemetryEvent event) {
        String eventId = event.eventId() != null && !event.eventId().isBlank()
                ? event.eventId()
                : UUID.randomUUID().toString();
        Instant timestamp = event.timestamp() != null ? event.timestamp() : Instant.now();
        String correlationId = event.correlationId() != null && !event.correlationId().isBlank()
                ? event.correlationId()
                : CorrelationId.currentOrNew();

        // Adopt the event's correlation id so this request's own logs match the event's lineage.
        CorrelationId.set(correlationId);

        return new TelemetryEvent(
                eventId,
                timestamp,
                event.service(),
                event.environment(),
                event.type(),
                event.endpoint(),
                event.statusCode(),
                event.latencyMs(),
                event.message(),
                event.traceId(),
                correlationId,
                event.deploymentVersion());
    }
}
