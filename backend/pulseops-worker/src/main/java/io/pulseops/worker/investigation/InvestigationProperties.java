package io.pulseops.worker.investigation;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulseops.investigation")
public class InvestigationProperties {

    private boolean enabled = true;

    /**
     * How long to wait after an incident changes before investigating.
     *
     * <p>An alert storm produces many incident updates in seconds. Investigating each one would
     * mean many LLM calls describing almost the same state. Waiting briefly collapses a burst into
     * a single investigation over the latest evidence.
     */
    private Duration debounce = Duration.ofSeconds(30);

    /** Telemetry is collected from this long before the incident opened, to capture the onset. */
    private Duration telemetryWindowBefore = Duration.ofMinutes(15);

    /**
     * The Incident Store and this worker consume the incidents topic independently, so an event can
     * arrive here before the store has committed the incident. These bound the wait for it.
     */
    private int maxStateLoadAttempts = 5;

    private Duration stateLoadBackoff = Duration.ofSeconds(2);

    private int workerThreads = 2;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getDebounce() {
        return debounce;
    }

    public void setDebounce(Duration debounce) {
        this.debounce = debounce;
    }

    public Duration getTelemetryWindowBefore() {
        return telemetryWindowBefore;
    }

    public void setTelemetryWindowBefore(Duration telemetryWindowBefore) {
        this.telemetryWindowBefore = telemetryWindowBefore;
    }

    public int getMaxStateLoadAttempts() {
        return maxStateLoadAttempts;
    }

    public void setMaxStateLoadAttempts(int maxStateLoadAttempts) {
        this.maxStateLoadAttempts = maxStateLoadAttempts;
    }

    public Duration getStateLoadBackoff() {
        return stateLoadBackoff;
    }

    public void setStateLoadBackoff(Duration stateLoadBackoff) {
        this.stateLoadBackoff = stateLoadBackoff;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }
}
