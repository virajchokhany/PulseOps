package io.pulseops.shopflow.common.telemetry;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import io.pulseops.domain.CorrelationId;
import io.pulseops.domain.telemetry.TelemetryEvent;
import io.pulseops.domain.telemetry.TelemetryType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Ships telemetry to PulseOps without ever affecting the request it describes.
 *
 * <p>Emitting is a non-blocking offer onto a bounded queue; a single daemon thread drains it. Two
 * deliberate consequences:
 * <ul>
 *   <li>If PulseOps is slow or down, ShopFlow latency is unchanged. Monitoring must not be able to
 *       take down the thing it monitors.</li>
 *   <li>If the queue fills, telemetry is <em>dropped</em> and counted, rather than growing the heap
 *       without bound. Losing observability beats losing the service.</li>
 * </ul>
 */
public class TelemetryPublisher {

    private static final Logger log = LoggerFactory.getLogger(TelemetryPublisher.class);
    private static final TelemetryEvent POISON = new TelemetryEvent(
            "shutdown", Instant.EPOCH, "-", "-", TelemetryType.BUSINESS, null, null, null, null, null, null, null);

    private final TelemetryProperties properties;
    private final RestClient restClient;
    private final BlockingQueue<TelemetryEvent> queue;
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    private volatile Thread worker;
    private volatile boolean running = true;

    public TelemetryPublisher(TelemetryProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
        this.queue = new ArrayBlockingQueue<>(properties.getQueueCapacity());
    }

    @PostConstruct
    public void start() {
        if (!properties.isEnabled()) {
            log.info("Telemetry publishing is disabled");
            return;
        }
        worker = new Thread(this::drain, "telemetry-publisher");
        worker.setDaemon(true);
        worker.start();
        log.info("Telemetry publisher started service={} ingestUrl={}", properties.getService(), properties.getIngestUrl());
    }

    public void emitHttp(String endpoint, int statusCode, long latencyMs, String message) {
        emit(new TelemetryEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                properties.getService(),
                properties.getEnvironment(),
                TelemetryType.HTTP,
                endpoint,
                statusCode,
                (int) latencyMs,
                message,
                null,
                CorrelationId.current(),
                properties.getDeploymentVersion()));
    }

    public void emitDependency(String target, int statusCode, long latencyMs, String message) {
        emit(new TelemetryEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                properties.getService(),
                properties.getEnvironment(),
                TelemetryType.DEPENDENCY,
                target,
                statusCode,
                (int) latencyMs,
                message,
                null,
                CorrelationId.current(),
                properties.getDeploymentVersion()));
    }

    public void emit(TelemetryEvent event) {
        if (!properties.isEnabled()) {
            return;
        }
        if (!queue.offer(event)) {
            long total = dropped.incrementAndGet();
            // Logged sparsely: a telemetry backlog must not turn into a log flood.
            if (total == 1 || total % 500 == 0) {
                log.warn("Telemetry queue full, dropped {} events so far", total);
            }
        }
    }

    private void drain() {
        while (running) {
            try {
                TelemetryEvent event = queue.poll(1, TimeUnit.SECONDS);
                if (event == null || event == POISON) {
                    continue;
                }
                send(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void send(TelemetryEvent event) {
        try {
            restClient.post()
                    .uri("/api/telemetry")
                    .body(event)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            long total = failed.incrementAndGet();
            if (total == 1 || total % 100 == 0) {
                log.warn("Failed to publish telemetry ({} failures so far): {}", total, e.getMessage());
            }
        }
    }

    public long droppedCount() {
        return dropped.get();
    }

    public long failedCount() {
        return failed.get();
    }

    @PreDestroy
    public void stop() {
        running = false;
        queue.offer(POISON);
        Thread current = worker;
        if (current != null) {
            current.interrupt();
        }
    }
}
