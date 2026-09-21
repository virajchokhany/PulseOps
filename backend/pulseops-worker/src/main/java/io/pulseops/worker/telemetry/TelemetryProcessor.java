package io.pulseops.worker.telemetry;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.pulseops.domain.ConsumerGroups;
import io.pulseops.domain.CorrelationId;
import io.pulseops.domain.Topics;
import io.pulseops.domain.telemetry.TelemetryEvent;
import io.pulseops.infrastructure.idempotency.IdempotencyGuard;
import io.pulseops.infrastructure.kafka.EventPayloadCodec;
import io.pulseops.infrastructure.kafka.NonRetryableEventException;
import io.pulseops.infrastructure.telemetry.TelemetryEventEntity;
import io.pulseops.infrastructure.telemetry.TelemetryEventRepository;

/**
 * Telemetry Processor: persists the telemetry stream.
 *
 * <p>Runs in its own consumer group, so it reads the {@code telemetry} topic completely
 * independently of the Alert Engine. If this processor falls behind on database writes, alerting is
 * unaffected — which is the point, because alerting is the time-critical path and storage is not.
 */
@Component
public class TelemetryProcessor {

    private static final Logger log = LoggerFactory.getLogger(TelemetryProcessor.class);

    private final EventPayloadCodec codec;
    private final IdempotencyGuard idempotencyGuard;
    private final TelemetryEventRepository repository;

    public TelemetryProcessor(EventPayloadCodec codec,
                              IdempotencyGuard idempotencyGuard,
                              TelemetryEventRepository repository) {
        this.codec = codec;
        this.idempotencyGuard = idempotencyGuard;
        this.repository = repository;
    }

    /**
     * Transactional so the idempotency claim and the telemetry insert commit or roll back together.
     * If they could commit separately, a failed insert would leave the event marked as processed and
     * the retry would silently skip it.
     */
    @KafkaListener(
            topics = Topics.TELEMETRY,
            groupId = ConsumerGroups.TELEMETRY_PROCESSOR,
            containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void handle(ConsumerRecord<String, String> record) {
        EventPayloadCodec.adoptCorrelationId(record);
        try {
            TelemetryEvent event = codec.decode(record.value(), TelemetryEvent.class);
            validate(event, record);

            if (!idempotencyGuard.claim(ConsumerGroups.TELEMETRY_PROCESSOR, event.eventId())) {
                log.debug("Telemetry event already processed, skipping eventId={}", event.eventId());
                return;
            }

            repository.save(TelemetryEventEntity.from(event));
            log.debug("Stored telemetry eventId={} service={} status={} latencyMs={}",
                    event.eventId(), event.service(), event.statusCode(), event.latencyMs());
        } finally {
            CorrelationId.clear();
        }
    }

    /**
     * Rejects events that could never be stored. These are thrown as non-retryable so the error
     * handler routes them to telemetry.dlq immediately instead of retrying a guaranteed failure.
     */
    private void validate(TelemetryEvent event, ConsumerRecord<String, String> record) {
        if (event.eventId() == null || event.eventId().isBlank()) {
            throw new NonRetryableEventException(
                    "Telemetry event without eventId at " + record.topic() + "-" + record.partition()
                            + "@" + record.offset());
        }
        if (event.service() == null || event.service().isBlank()) {
            throw new NonRetryableEventException("Telemetry event " + event.eventId() + " has no service");
        }
        if (event.timestamp() == null) {
            throw new NonRetryableEventException("Telemetry event " + event.eventId() + " has no timestamp");
        }
        if (event.type() == null) {
            throw new NonRetryableEventException("Telemetry event " + event.eventId() + " has no type");
        }
        if (event.environment() == null || event.environment().isBlank()) {
            throw new NonRetryableEventException("Telemetry event " + event.eventId() + " has no environment");
        }
    }
}
