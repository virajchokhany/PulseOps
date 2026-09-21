package io.pulseops.domain;

/**
 * Kafka consumer group ids.
 *
 * <p>Every logical consumer gets its own group so that components reading the same topic make
 * progress independently. The Alert Engine must never be blocked by the Telemetry Processor's
 * database writes, and the AI Investigation Worker must never be blocked by the Incident Store.
 */
public final class ConsumerGroups {

    public static final String TELEMETRY_PROCESSOR = "pulseops-telemetry-processor";
    public static final String ALERT_ENGINE = "pulseops-alert-engine";
    public static final String INCIDENT_CORRELATION = "pulseops-incident-correlation";
    public static final String INCIDENT_STORE = "pulseops-incident-store";
    public static final String AI_INVESTIGATION = "pulseops-ai-investigation";

    private ConsumerGroups() {
    }
}
