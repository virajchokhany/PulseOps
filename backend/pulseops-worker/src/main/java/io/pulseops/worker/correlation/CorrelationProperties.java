package io.pulseops.worker.correlation;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulseops.correlation")
public class CorrelationProperties {

    private boolean enabled = true;

    /**
     * How long an incident stays open to new alerts. An alert arriving after this window starts a
     * new incident rather than reviving an old one, so yesterday's outage cannot absorb today's.
     */
    private Duration window = Duration.ofMinutes(10);

    /** Bound on the remembered alert ids used to ignore Kafka redeliveries. */
    private int recentAlertCacheSize = 2000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getWindow() {
        return window;
    }

    public void setWindow(Duration window) {
        this.window = window;
    }

    public int getRecentAlertCacheSize() {
        return recentAlertCacheSize;
    }

    public void setRecentAlertCacheSize(int recentAlertCacheSize) {
        this.recentAlertCacheSize = recentAlertCacheSize;
    }
}
