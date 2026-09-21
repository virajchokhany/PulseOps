package io.pulseops.worker.correlation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.pulseops.domain.ConsumerGroups;
import io.pulseops.domain.CorrelationId;
import io.pulseops.domain.IncidentStatus;
import io.pulseops.domain.Topics;
import io.pulseops.domain.alert.AlertEvent;
import io.pulseops.domain.incident.IncidentEvent;
import io.pulseops.domain.incident.IncidentEventType;
import io.pulseops.infrastructure.catalog.ServiceDependencyGraph;
import io.pulseops.infrastructure.incident.IncidentEntity;
import io.pulseops.infrastructure.incident.IncidentRepository;
import io.pulseops.infrastructure.kafka.EventPayloadCodec;
import io.pulseops.infrastructure.kafka.EventPublisher;

/**
 * Turns a stream of alerts into a small number of incidents.
 *
 * <p>Event-driven: it consumes the {@code alerts} topic and never polls the database looking for
 * work. An alert either extends an open incident or starts a new one.
 *
 * <p>An alert joins an existing incident when it is recent enough <em>and</em> its service is
 * already involved or sits on a direct edge of the dependency graph from a service that is. That
 * second condition is what collapses "payment is erroring", "payment is slow" and "orders are
 * failing" into one incident instead of three.
 *
 * <p><b>State.</b> Open incidents are held in memory and reloaded from the database on startup.
 * Correlation cannot read its own writes from the Incident Store, because the store is a separate
 * consumer group that commits asynchronously; asking the database "is there an open incident for
 * this service?" immediately after publishing a create would frequently answer no and produce a
 * duplicate incident. In-memory state is authoritative for the correlation decision.
 *
 * <p><b>Tradeoff.</b> That makes correlation effectively single-instance. Running two instances
 * would split the state and could open two incidents for one outage. The fix is partition-affine
 * state or a shared store; neither is needed to demonstrate the design, so the limit is documented
 * rather than hidden.
 */
@Component
@EnableConfigurationProperties(CorrelationProperties.class)
public class IncidentCorrelationEngine {

    private static final Logger log = LoggerFactory.getLogger(IncidentCorrelationEngine.class);

    private final CorrelationProperties properties;
    private final EventPayloadCodec codec;
    private final EventPublisher eventPublisher;
    private final ServiceDependencyGraph dependencyGraph;
    private final IncidentRepository incidentRepository;
    private final MeterRegistry meterRegistry;

    private final Object lock = new Object();
    private final Map<UUID, ActiveIncident> activeIncidents = new LinkedHashMap<>();
    private final Map<String, Boolean> recentAlertIds;

    public IncidentCorrelationEngine(CorrelationProperties properties,
                                     EventPayloadCodec codec,
                                     EventPublisher eventPublisher,
                                     ServiceDependencyGraph dependencyGraph,
                                     IncidentRepository incidentRepository,
                                     MeterRegistry meterRegistry) {
        this.properties = properties;
        this.codec = codec;
        this.eventPublisher = eventPublisher;
        this.dependencyGraph = dependencyGraph;
        this.incidentRepository = incidentRepository;
        this.meterRegistry = meterRegistry;
        this.recentAlertIds = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > IncidentCorrelationEngine.this.properties.getRecentAlertCacheSize();
            }
        });
    }

    /** Rebuilds working state after a restart so a redeploy mid-incident does not fork it. */
    @EventListener(ApplicationReadyEvent.class)
    public void restoreOpenIncidents() {
        List<IncidentEntity> open = incidentRepository.findByStatusOrderByOpenedAtDesc(IncidentStatus.OPEN);
        synchronized (lock) {
            for (IncidentEntity incident : open) {
                ActiveIncident restored = new ActiveIncident(
                        incident.getIncidentKey(), incident.getTitle(), incident.getSeverity(),
                        incident.getPrimaryService(), incident.getOpenedAt(), incident.getCorrelationId());
                incident.getAffectedServices().forEach(restored::addService);
                activeIncidents.put(incident.getIncidentKey(), restored);
            }
        }
        if (!open.isEmpty()) {
            log.info("Restored {} open incident(s) into correlation state", open.size());
        }
    }

    @KafkaListener(topics = Topics.ALERTS, groupId = ConsumerGroups.INCIDENT_CORRELATION)
    public void handle(ConsumerRecord<String, String> record) {
        if (!properties.isEnabled()) {
            return;
        }
        EventPayloadCodec.adoptCorrelationId(record);
        try {
            AlertEvent alert = codec.decode(record.value(), AlertEvent.class);
            if (alert.alertId() == null || alert.service() == null) {
                log.warn("Ignoring alert without alertId or service at offset {}", record.offset());
                return;
            }
            if (recentAlertIds.putIfAbsent(alert.alertId(), Boolean.TRUE) != null) {
                log.debug("Duplicate alert redelivery ignored alertId={}", alert.alertId());
                return;
            }
            correlate(alert);
        } finally {
            CorrelationId.clear();
        }
    }

    /**
     * Serialised because correlation is a read-modify-write over shared state, and two alerts
     * arriving together for related services must not both decide they are first. Alerts are rare,
     * so a single lock costs nothing.
     */
    private void correlate(AlertEvent alert) {
        synchronized (lock) {
            Instant now = Instant.now();
            expireStale(now);

            Optional<ActiveIncident> match = findMatch(alert, now);
            if (match.isPresent()) {
                update(match.get(), alert, now);
            } else {
                create(alert, now);
            }
        }
    }

    private Optional<ActiveIncident> findMatch(AlertEvent alert, Instant now) {
        return activeIncidents.values().stream()
                .filter(incident -> isRelated(incident, alert.service()))
                .max(Comparator.comparing(ActiveIncident::lastAlertAt));
    }

    private boolean isRelated(ActiveIncident incident, String service) {
        if (incident.involves(service)) {
            return true;
        }
        return incident.affectedServices().stream()
                .anyMatch(affected -> dependencyGraph.areRelated(affected, service));
    }

    private void create(AlertEvent alert, Instant now) {
        UUID incidentKey = UUID.randomUUID();
        String title = titleFor(alert.service());
        ActiveIncident incident = new ActiveIncident(
                incidentKey, title, alert.severity(), alert.service(), now, CorrelationId.currentOrNew());
        activeIncidents.put(incidentKey, incident);

        publish(IncidentEventType.CREATED, incident, alert, now);
        meterRegistry.counter("pulseops.incidents.created", "service", alert.service()).increment();
        log.warn("INCIDENT CREATED key={} title='{}' severity={} trigger={}",
                incidentKey, title, alert.severity(), alert.alertType());
    }

    private void update(ActiveIncident incident, AlertEvent alert, Instant now) {
        incident.addService(alert.service());
        incident.escalateTo(alert.severity());

        // The alert's service is a dependency of the current primary, so it sits further
        // downstream and is the better root-cause candidate. order-service failing because
        // payment-service is failing should read as a payment incident.
        if (dependencyGraph.dependsOn(incident.primaryService(), alert.service())) {
            incident.promotePrimary(alert.service(), titleFor(alert.service()));
            log.info("Promoted primary service of incident {} to {}", incident.incidentKey(), alert.service());
        }

        incident.recordAlert(now);
        publish(IncidentEventType.UPDATED, incident, alert, now);
        meterRegistry.counter("pulseops.incidents.updated", "service", alert.service()).increment();
        log.warn("INCIDENT UPDATED key={} alerts={} severity={} trigger={}",
                incident.incidentKey(), incident.alertCount(), incident.severity(), alert.alertType());
    }

    private void publish(IncidentEventType type, ActiveIncident incident, AlertEvent alert, Instant now) {
        IncidentEvent event = new IncidentEvent(
                "incident-evt-" + UUID.randomUUID(),
                type,
                incident.incidentKey(),
                now,
                incident.title(),
                IncidentStatus.OPEN,
                incident.severity(),
                incident.primaryService(),
                new ArrayList<>(incident.affectedServices()),
                incident.alertCount(),
                incident.version(),
                alert,
                incident.correlationId());

        // Keyed by incident so every event for one incident lands on the same partition and is
        // therefore delivered in order. Without this, an UPDATED could overtake its CREATED.
        eventPublisher.publish(Topics.INCIDENTS, incident.incidentKey().toString(), event);
    }

    private void expireStale(Instant now) {
        Duration window = properties.getWindow();
        activeIncidents.values().removeIf(incident -> {
            boolean expired = Duration.between(incident.lastAlertAt(), now).compareTo(window) > 0;
            if (expired) {
                log.info("Incident {} left the correlation window after {}", incident.incidentKey(), window);
            }
            return expired;
        });
    }

    private String titleFor(String service) {
        return dependencyGraph.displayNameOf(service) + " Degradation";
    }
}
