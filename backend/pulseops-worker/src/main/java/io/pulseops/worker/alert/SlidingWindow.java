package io.pulseops.worker.alert;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Time-bounded view of one service's recent HTTP samples.
 *
 * <p>Held in memory rather than computed from PostgreSQL. Querying the telemetry table on every
 * event would couple alerting to the Telemetry Processor's write throughput, which is exactly the
 * coupling the separate consumer groups exist to avoid.
 *
 * <p><b>Tradeoff.</b> In-memory state is lost on restart and is per-instance, so the Alert Engine is
 * effectively single-instance for correct rates. That is an acceptable MVP limitation: the fix is a
 * shared store or a partitioned key-value state store, and neither is needed to demonstrate the
 * architecture. It is called out rather than hidden.
 */
class SlidingWindow {

    private record Sample(long epochMillis, String eventId, boolean error, int latencyMs) {
    }

    private final Deque<Sample> samples = new ArrayDeque<>();

    /** Event ids currently inside the window, so a Kafka redelivery cannot skew the rate twice. */
    private final Set<String> eventIds = new HashSet<>();

    private final long windowMillis;
    private final int maxSamples;

    SlidingWindow(long windowMillis, int maxSamples) {
        this.windowMillis = windowMillis;
        this.maxSamples = maxSamples;
    }

    /** @return false when this event id is already in the window (duplicate delivery). */
    synchronized boolean record(String eventId, Instant occurredAt, boolean error, Integer latencyMs) {
        long now = System.currentTimeMillis();
        evictOlderThan(now - windowMillis);

        if (!eventIds.add(eventId)) {
            return false;
        }

        samples.addLast(new Sample(occurredAt.toEpochMilli(), eventId, error, latencyMs != null ? latencyMs : 0));

        // Hard cap: a traffic spike must not turn the window into an unbounded buffer.
        while (samples.size() > maxSamples) {
            Sample evicted = samples.removeFirst();
            eventIds.remove(evicted.eventId());
        }
        return true;
    }

    synchronized WindowSnapshot snapshot() {
        long now = System.currentTimeMillis();
        evictOlderThan(now - windowMillis);

        int windowSeconds = (int) (windowMillis / 1000);
        if (samples.isEmpty()) {
            return WindowSnapshot.empty(windowSeconds);
        }

        int count = samples.size();
        int errors = 0;
        long latencySum = 0;
        int[] latencies = new int[count];
        int index = 0;

        for (Sample sample : samples) {
            if (sample.error()) {
                errors++;
            }
            latencySum += sample.latencyMs();
            latencies[index++] = sample.latencyMs();
        }

        Arrays.sort(latencies);
        int p95Index = Math.min(count - 1, (int) Math.ceil(count * 0.95) - 1);

        return new WindowSnapshot(
                count,
                errors,
                (double) errors / count,
                (double) latencySum / count,
                latencies[Math.max(p95Index, 0)],
                windowSeconds);
    }

    private void evictOlderThan(long cutoffMillis) {
        while (!samples.isEmpty() && samples.peekFirst().epochMillis() < cutoffMillis) {
            Sample evicted = samples.removeFirst();
            eventIds.remove(evicted.eventId());
        }
    }
}
