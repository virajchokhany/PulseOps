package io.pulseops.shopflow.payment.failure;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Controlled failure injection for the demo.
 *
 * <p>Models a degraded <em>external payment provider</em>, not a bug in this service: the provider
 * gets slower and starts refusing requests. That distinction matters, because the whole point of the
 * demo is whether PulseOps can work out where the fault actually lives.
 *
 * <p>Off by default and never enabled by configuration alone.
 */
@Component
public class FailureInjector {

    private static final Logger log = LoggerFactory.getLogger(FailureInjector.class);

    public record Settings(boolean enabled, int errorRate, int latencyMs) {

        public static final Settings DISABLED = new Settings(false, 0, 0);
    }

    private final AtomicReference<Settings> settings = new AtomicReference<>(Settings.DISABLED);

    public Settings current() {
        return settings.get();
    }

    public void apply(int errorRate, int latencyMs) {
        if (errorRate < 0 || errorRate > 100) {
            throw new IllegalArgumentException("errorRate must be between 0 and 100");
        }
        if (latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs must not be negative");
        }
        Settings applied = new Settings(true, errorRate, latencyMs);
        settings.set(applied);
        log.warn("Failure injection ENABLED errorRate={}% extraLatencyMs={}", errorRate, latencyMs);
    }

    public void reset() {
        settings.set(Settings.DISABLED);
        log.warn("Failure injection reset to disabled");
    }

    /** Extra latency, in milliseconds, that the simulated provider should add to this call. */
    public int extraLatencyMs() {
        Settings active = settings.get();
        return active.enabled() ? active.latencyMs() : 0;
    }

    /** Whether this particular call should be refused by the simulated provider. */
    public boolean shouldFail() {
        Settings active = settings.get();
        if (!active.enabled() || active.errorRate() <= 0) {
            return false;
        }
        return ThreadLocalRandom.current().nextInt(100) < active.errorRate();
    }
}
