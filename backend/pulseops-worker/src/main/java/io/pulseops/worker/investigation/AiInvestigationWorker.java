package io.pulseops.worker.investigation;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.pulseops.domain.ConsumerGroups;
import io.pulseops.domain.CorrelationId;
import io.pulseops.domain.Topics;
import io.pulseops.domain.evidence.InvestigationContext;
import io.pulseops.domain.incident.IncidentEvent;
import io.pulseops.domain.incident.IncidentEventType;
import io.pulseops.domain.rca.RootCauseAnalysis;
import io.pulseops.infrastructure.incident.IncidentEntity;
import io.pulseops.infrastructure.investigation.AiInvestigationEntity;
import io.pulseops.infrastructure.kafka.EventPayloadCodec;
import jakarta.annotation.PreDestroy;

/**
 * Investigates incidents automatically.
 *
 * <p>Consumes the {@code incidents} topic in its own consumer group, independently of the Incident
 * Store. There is no "Investigate" button: an incident that changes gets re-analysed on its own.
 *
 * <p>Two problems shape this class.
 *
 * <p><b>Alert storms.</b> A degradation produces many incident updates within seconds. Investigating
 * each would mean many near-identical LLM calls. Work is therefore debounced: the first change
 * schedules an investigation a short time ahead, and every change arriving before that deadline is
 * absorbed into it. The deadline is not extended by later changes, so a sustained storm cannot
 * postpone the analysis indefinitely.
 *
 * <p><b>Eventual consistency.</b> This worker and the Incident Store read the same topic in
 * different consumer groups, so an event can arrive here before the store has committed the
 * incident. Kafka ordering says nothing about another consumer's database transactions. The worker
 * therefore retries the state load with backoff and, if the incident never appears, records a
 * failed investigation instead of silently doing nothing.
 */
@Component
@EnableConfigurationProperties(InvestigationProperties.class)
public class AiInvestigationWorker {

    private static final Logger log = LoggerFactory.getLogger(AiInvestigationWorker.class);

    private final InvestigationProperties properties;
    private final EventPayloadCodec codec;
    private final InvestigationStore store;
    private final EvidenceCollector evidenceCollector;
    private final io.pulseops.worker.investigation.llm.LlmProvider llmProvider;
    private final MeterRegistry meterRegistry;

    private final ScheduledExecutorService scheduler;
    private final Map<UUID, PendingInvestigation> pending = new ConcurrentHashMap<>();

    private record PendingInvestigation(long incidentVersion, String correlationId) {
    }

    public AiInvestigationWorker(InvestigationProperties properties,
                                 EventPayloadCodec codec,
                                 InvestigationStore store,
                                 EvidenceCollector evidenceCollector,
                                 io.pulseops.worker.investigation.llm.LlmProvider llmProvider,
                                 MeterRegistry meterRegistry) {
        this.properties = properties;
        this.codec = codec;
        this.store = store;
        this.evidenceCollector = evidenceCollector;
        this.llmProvider = llmProvider;
        this.meterRegistry = meterRegistry;
        this.scheduler = Executors.newScheduledThreadPool(properties.getWorkerThreads(), runnable -> {
            Thread thread = new Thread(runnable, "ai-investigation");
            thread.setDaemon(true);
            return thread;
        });
    }

    @KafkaListener(topics = Topics.INCIDENTS, groupId = ConsumerGroups.AI_INVESTIGATION)
    public void handle(ConsumerRecord<String, String> record) {
        if (!properties.isEnabled()) {
            return;
        }
        String correlationId = EventPayloadCodec.adoptCorrelationId(record);
        try {
            IncidentEvent event = codec.decode(record.value(), IncidentEvent.class);
            if (event.incidentKey() == null) {
                return;
            }
            if (event.type() == IncidentEventType.RESOLVED) {
                pending.remove(event.incidentKey());
                log.info("Incident {} resolved; cancelling any pending investigation", event.incidentKey());
                return;
            }
            schedule(event, correlationId);
        } finally {
            CorrelationId.clear();
        }
    }

    private void schedule(IncidentEvent event, String correlationId) {
        // The RCA is invalidated immediately so the dashboard shows STALE while the debounce runs,
        // rather than presenting a stale conclusion as current.
        store.markStale(event.incidentKey());

        PendingInvestigation existing = pending.putIfAbsent(
                event.incidentKey(), new PendingInvestigation(event.version(), correlationId));

        if (existing != null) {
            log.debug("Coalescing incident {} v{} into the pending investigation",
                    event.incidentKey(), event.version());
            return;
        }

        long delayMillis = properties.getDebounce().toMillis();
        scheduler.schedule(() -> investigate(event.incidentKey()), delayMillis, TimeUnit.MILLISECONDS);
        log.info("Scheduled investigation of incident {} in {}ms", event.incidentKey(), delayMillis);
    }

    private void investigate(UUID incidentKey) {
        PendingInvestigation job = pending.remove(incidentKey);
        if (job == null) {
            return;
        }
        CorrelationId.set(job.correlationId());
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            Optional<IncidentEntity> loaded = loadWithRetry(incidentKey);
            if (loaded.isEmpty()) {
                String reason = "Incident state never became available after %d attempts"
                        .formatted(properties.getMaxStateLoadAttempts());
                log.error("Abandoning investigation of {}: {}", incidentKey, reason);
                store.recordUnavailableIncident(incidentKey, job.incidentVersion(), reason, job.correlationId());
                meterRegistry.counter("pulseops.investigations.failed", "reason", "state_unavailable").increment();
                return;
            }

            runInvestigation(loaded.get(), job);
        } catch (Throwable t) {
            // A ScheduledExecutorService silently discards anything thrown by its task, so an
            // unexpected failure here would vanish without a log line or a status change. Catching
            // at the boundary is the only way this stays visible.
            log.error("Unexpected failure investigating incident {}", incidentKey, t);
            meterRegistry.counter("pulseops.investigations.failed", "reason", "unexpected").increment();
        } finally {
            sample.stop(meterRegistry.timer("pulseops.investigations.duration"));
            CorrelationId.clear();
        }
    }

    private void runInvestigation(IncidentEntity incident, PendingInvestigation job) {
        long startedAt = System.nanoTime();
        AiInvestigationEntity investigation;
        try {
            investigation = store.begin(incident.getIncidentKey(), incident.getVersion(), job.correlationId());
        } catch (RuntimeException e) {
            log.error("Could not open an investigation record for incident {}",
                    incident.getIncidentKey(), e);
            meterRegistry.counter("pulseops.investigations.failed", "reason", "begin_failed").increment();
            return;
        }

        try {
            InvestigationContext context = evidenceCollector.collect(incident);
            RootCauseAnalysis analysis = llmProvider.analyse(context);
            long durationMs = elapsedMs(startedAt);

            store.complete(investigation.getId(), incident.getIncidentKey(), incident.getVersion(),
                    analysis, context, llmProvider.name(), llmProvider.model(), durationMs);

            meterRegistry.counter("pulseops.investigations.completed", "provider", llmProvider.name())
                    .increment();
            log.warn("RCA COMPLETED incident={} confidence={} provider={} durationMs={}",
                    incident.getIncidentNumber(), analysis.confidence(), llmProvider.name(), durationMs);
        } catch (RuntimeException e) {
            long durationMs = elapsedMs(startedAt);
            // Never swallowed: a failed investigation is visible in the incident's RCA status,
            // its timeline, and the investigation row's failure reason.
            log.error("Investigation failed for incident {}: {}", incident.getIncidentKey(), e.getMessage(), e);
            store.fail(investigation.getId(), incident.getIncidentKey(), e.getMessage(), durationMs);
            meterRegistry.counter("pulseops.investigations.failed", "reason", "analysis_error").increment();
        }
    }

    /** Bounded wait for the Incident Store to catch up. */
    private Optional<IncidentEntity> loadWithRetry(UUID incidentKey) {
        for (int attempt = 1; attempt <= properties.getMaxStateLoadAttempts(); attempt++) {
            Optional<IncidentEntity> incident = store.loadIncident(incidentKey);
            if (incident.isPresent()) {
                if (attempt > 1) {
                    log.info("Incident {} became available on attempt {}", incidentKey, attempt);
                }
                return incident;
            }
            if (attempt == properties.getMaxStateLoadAttempts()) {
                break;
            }
            log.debug("Incident {} not yet visible (attempt {}); backing off", incidentKey, attempt);
            try {
                Thread.sleep(properties.getStateLoadBackoff().toMillis() * attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
