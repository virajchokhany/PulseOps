package io.pulseops.worker.alert;

import io.pulseops.domain.alert.AlertMetric;

/** Aggregated view of one service's recent HTTP behaviour. */
public record WindowSnapshot(
        int sampleCount,
        int errorCount,
        double errorRate,
        double avgLatencyMs,
        double p95LatencyMs,
        int windowSeconds) {

    public static WindowSnapshot empty(int windowSeconds) {
        return new WindowSnapshot(0, 0, 0, 0, 0, windowSeconds);
    }

    public double valueOf(AlertMetric metric) {
        return switch (metric) {
            case ERROR_RATE -> errorRate;
            case AVG_LATENCY_MS -> avgLatencyMs;
            case P95_LATENCY_MS -> p95LatencyMs;
        };
    }
}
