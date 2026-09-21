package io.pulseops.worker.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.pulseops.domain.alert.AlertMetric;

class SlidingWindowTest {

    private static final long ONE_MINUTE_MILLIS = 60_000L;

    @Test
    void computesErrorRateOverTheWindow() {
        SlidingWindow window = new SlidingWindow(ONE_MINUTE_MILLIS, 1000);
        Instant now = Instant.now();

        for (int i = 0; i < 8; i++) {
            window.record("ok-" + i, now, false, 100);
        }
        for (int i = 0; i < 2; i++) {
            window.record("err-" + i, now, true, 100);
        }

        WindowSnapshot snapshot = window.snapshot();
        assertThat(snapshot.sampleCount()).isEqualTo(10);
        assertThat(snapshot.errorCount()).isEqualTo(2);
        assertThat(snapshot.errorRate()).isEqualTo(0.20, within(0.0001));
        assertThat(snapshot.valueOf(AlertMetric.ERROR_RATE)).isEqualTo(0.20, within(0.0001));
    }

    @Test
    void p95IgnoresTheFastMajority() {
        SlidingWindow window = new SlidingWindow(ONE_MINUTE_MILLIS, 1000);
        Instant now = Instant.now();

        // 90 fast requests and 10 slow ones. The average stays comfortably low while the p95
        // reports what the slowest tenth of users actually experienced.
        for (int i = 0; i < 90; i++) {
            window.record("fast-" + i, now, false, 100);
        }
        for (int i = 0; i < 10; i++) {
            window.record("slow-" + i, now, false, 2500);
        }

        WindowSnapshot snapshot = window.snapshot();
        assertThat(snapshot.avgLatencyMs()).isEqualTo(340.0, within(0.001));
        assertThat(snapshot.p95LatencyMs()).isEqualTo(2500);
    }

    @Test
    void p95UsesNearestRankSoTheBoundarySampleCounts() {
        SlidingWindow window = new SlidingWindow(ONE_MINUTE_MILLIS, 1000);
        Instant now = Instant.now();

        // Exactly 95% fast: nearest rank puts the p95 on the last fast sample, because 95% of
        // requests genuinely were at or below 100ms. Pinned so the definition cannot drift.
        for (int i = 0; i < 95; i++) {
            window.record("fast-" + i, now, false, 100);
        }
        for (int i = 0; i < 5; i++) {
            window.record("slow-" + i, now, false, 2500);
        }

        assertThat(window.snapshot().p95LatencyMs()).isEqualTo(100);
    }

    @Test
    void rejectsDuplicateEventIds() {
        SlidingWindow window = new SlidingWindow(ONE_MINUTE_MILLIS, 1000);
        Instant now = Instant.now();

        assertThat(window.record("event-1", now, true, 100)).isTrue();
        assertThat(window.record("event-1", now, true, 100)).isFalse();

        assertThat(window.snapshot().sampleCount()).isEqualTo(1);
    }

    @Test
    void evictsSamplesOlderThanTheWindow() {
        SlidingWindow window = new SlidingWindow(ONE_MINUTE_MILLIS, 1000);
        Instant now = Instant.now();

        window.record("old", now.minusSeconds(120), true, 100);
        window.record("fresh", now, false, 100);

        WindowSnapshot snapshot = window.snapshot();
        assertThat(snapshot.sampleCount()).isEqualTo(1);
        assertThat(snapshot.errorCount()).isZero();
    }

    @Test
    void capsRetainedSamples() {
        SlidingWindow window = new SlidingWindow(ONE_MINUTE_MILLIS, 50);
        Instant now = Instant.now();

        for (int i = 0; i < 500; i++) {
            window.record("event-" + i, now, false, 100);
        }

        assertThat(window.snapshot().sampleCount()).isEqualTo(50);
    }

    @Test
    void emptyWindowReportsNothing() {
        SlidingWindow window = new SlidingWindow(ONE_MINUTE_MILLIS, 1000);

        WindowSnapshot snapshot = window.snapshot();
        assertThat(snapshot.sampleCount()).isZero();
        assertThat(snapshot.errorRate()).isZero();
    }
}
