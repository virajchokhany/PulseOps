package io.pulseops.worker.alert;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import io.pulseops.domain.Severity;
import io.pulseops.domain.alert.AlertMetric;

/**
 * Alert rules as configuration rather than code.
 *
 * <p>Thresholds are the part of an alerting system that changes most often and needs the least
 * ceremony to change. Keeping them in configuration means tuning a threshold is not a code change,
 * and the full rule set is visible in one place during an incident review.
 */
@ConfigurationProperties(prefix = "pulseops.alerting")
public class AlertingProperties {

    private boolean enabled = true;

    private Duration window = Duration.ofSeconds(60);

    /**
     * Below this many samples the window is ignored. Without it, the very first failed request in a
     * quiet period reads as a 100% error rate and fires a critical alert.
     */
    private int minSamples = 8;

    /**
     * How long a rule stays quiet after firing. Prevents one degradation from emitting an alert per
     * telemetry event; incident-level coalescing is a separate concern handled by the AI worker.
     */
    private Duration cooldown = Duration.ofSeconds(90);

    private int maxSamplesPerWindow = 5000;

    private List<Rule> rules = new ArrayList<>();

    public static class Rule {

        private String name;
        private String service;
        private AlertMetric metric;
        private double threshold;
        private Severity severity = Severity.MAJOR;
        private String description;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getService() {
            return service;
        }

        public void setService(String service) {
            this.service = service;
        }

        public AlertMetric getMetric() {
            return metric;
        }

        public void setMetric(AlertMetric metric) {
            this.metric = metric;
        }

        public double getThreshold() {
            return threshold;
        }

        public void setThreshold(double threshold) {
            this.threshold = threshold;
        }

        public Severity getSeverity() {
            return severity;
        }

        public void setSeverity(Severity severity) {
            this.severity = severity;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

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

    public int getMinSamples() {
        return minSamples;
    }

    public void setMinSamples(int minSamples) {
        this.minSamples = minSamples;
    }

    public Duration getCooldown() {
        return cooldown;
    }

    public void setCooldown(Duration cooldown) {
        this.cooldown = cooldown;
    }

    public int getMaxSamplesPerWindow() {
        return maxSamplesPerWindow;
    }

    public void setMaxSamplesPerWindow(int maxSamplesPerWindow) {
        this.maxSamplesPerWindow = maxSamplesPerWindow;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }
}
