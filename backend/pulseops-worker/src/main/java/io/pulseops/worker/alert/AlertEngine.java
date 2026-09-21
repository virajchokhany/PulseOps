package io.pulseops.worker.alert;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.pulseops.domain.ConsumerGroups;
import io.pulseops.domain.CorrelationId;
import io.pulseops.domain.Topics;
import io.pulseops.domain.alert.AlertEvent;
import io.pulseops.domain.alert.AlertMetric;
import io.pulseops.domain.telemetry.TelemetryEvent;
import io.pulseops.domain.telemetry.TelemetryType;
import io.pulseops.infrastructure.alert.AlertEntity;
import io.pulseops.infrastructure.alert.AlertRepository;
import io.pulseops.infrastructure.kafka.EventPayloadCodec;
import io.pulseops.infrastructure.kafka.EventPublisher;

/**
 * Deterministic alerting over the telemetry stream.
 *
 * <p>Reads {@code telemetry} in its own consumer group, completely independently of the Telemetry
 * Processor. Alerting is the latency-sensitive path; storage is not. If the processor's database
 * writes slow down, detection is unaffected.
 *
 * <p>Rules are plain arithmetic against a sliding window: no anomaly detection, no model. An
 * operator must be able to read a rule and predict exactly when it fires, and a rule that fired
 * yesterday must fire again today on the same data.
 */
@Component
@EnableConfigurationProperties(AlertingProperties.class)
public class AlertEngine {

    private static final Logger log = LoggerFactory.getLogger(AlertEngine.class);

    private final AlertingProperties properties;
    private final EventPayloadCodec codec;
    private final EventPublisher eventPublisher;
    private final AlertRepository alertRepository;
    private final MeterRegistry meterRegistry;

    private final Map<String, SlidingWindow> windows = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastFiredAt = new ConcurrentHashMap<>();

    public AlertEngine(AlertingProperties properties,
                       EventPayloadCodec codec,
                       EventPublisher eventPublisher,
                       AlertRepository alertRepository,
                       MeterRegistry meterRegistry) {
        this.properties = properties;
        this.codec = codec;
        this.eventPublisher = eventPublisher;
        this.alertRepository = alertRepository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Deliberately not {@code @Transactional}: the hot path is in-memory aggregation, and holding a
     * database transaction open for every telemetry event would reintroduce the coupling this
     * consumer group exists to avoid.
     */
    @KafkaListener(topics = Topics.TELEMETRY, groupId = ConsumerGroups.ALERT_ENGINE)
    public void handle(ConsumerRecord<String, String> record) {
        if (!properties.isEnabled()) {
            return;
        }
        EventPayloadCodec.adoptCorrelationId(record);
        try {
            TelemetryEvent event = codec.decode(record.value(), TelemetryEvent.class);

            // Only inbound HTTP counts. DEPENDENCY events describe a call this service *made*;
            // counting both would double-count a single failure and inflate the error rate.
            if (event.type() != TelemetryType.HTTP || event.service() == null) {
                return;
            }

            SlidingWindow window = windows.computeIfAbsent(event.service(), service ->
                    new SlidingWindow(properties.getWindow().toMillis(), properties.getMaxSamplesPerWindow()));

            boolean accepted = window.record(
                    event.eventId(), event.timestamp(), event.isServerError(), event.latencyMs());
            if (!accepted) {
                return;
            }

            evaluate(event.service(), window.snapshot());
        } finally {
            CorrelationId.clear();
        }
    }

    private void evaluate(String service, WindowSnapshot snapshot) {
        if (snapshot.sampleCount() < properties.getMinSamples()) {
            return;
        }

        List<AlertingProperties.Rule> rules = properties.getRules().stream()
                .filter(rule -> service.equals(rule.getService()))
                .toList();

        for (AlertingProperties.Rule rule : rules) {
            double value = snapshot.valueOf(rule.getMetric());
            if (value <= rule.getThreshold() || isCoolingDown(service, rule)) {
                continue;
            }
            raise(service, rule, value, snapshot);
        }
    }

    private boolean isCoolingDown(String service, AlertingProperties.Rule rule) {
        Instant last = lastFiredAt.get(cooldownKey(service, rule));
        return last != null && Duration.between(last, Instant.now()).compareTo(properties.getCooldown()) < 0;
    }

    private void raise(String service, AlertingProperties.Rule rule, double value, WindowSnapshot snapshot) {
        AlertEvent alert = new AlertEvent(
                "alert-" + UUID.randomUUID(),
                Instant.now(),
                service,
                rule.getName(),
                rule.getSeverity(),
                describe(rule, value, snapshot),
                rule.getMetric(),
                value,
                rule.getThreshold(),
                snapshot.windowSeconds(),
                snapshot.sampleCount(),
                CorrelationId.currentOrNew());

        // Persisted first so an alert visible in the UI is never one that was lost in transit.
        // This is not an outbox: a crash between the save and the publish would leave a stored
        // alert with no event. Accepted for the MVP, and the reason alerts carry their own id.
        alertRepository.save(AlertEntity.from(alert));
        eventPublisher.publish(Topics.ALERTS, service, alert);

        lastFiredAt.put(cooldownKey(service, rule), Instant.now());
        meterRegistry.counter("pulseops.alerts.generated", "service", service, "type", rule.getName())
                .increment();

        log.warn("ALERT {} service={} {}={} threshold={} samples={} alertId={}",
                rule.getName(), service, rule.getMetric(), format(rule.getMetric(), value),
                format(rule.getMetric(), rule.getThreshold()), snapshot.sampleCount(), alert.alertId());
    }

    private String describe(AlertingProperties.Rule rule, double value, WindowSnapshot snapshot) {
        if (rule.getDescription() != null && !rule.getDescription().isBlank()) {
            return rule.getDescription();
        }
        return "%s: %s is %s over the last %ds (threshold %s, %d samples, %d errors)".formatted(
                rule.getService(),
                rule.getMetric(),
                format(rule.getMetric(), value),
                snapshot.windowSeconds(),
                format(rule.getMetric(), rule.getThreshold()),
                snapshot.sampleCount(),
                snapshot.errorCount());
    }

    private static String format(AlertMetric metric, double value) {
        return metric == AlertMetric.ERROR_RATE
                ? "%.1f%%".formatted(value * 100)
                : "%.0fms".formatted(value);
    }

    private static String cooldownKey(String service, AlertingProperties.Rule rule) {
        return service + "/" + rule.getName();
    }
}
