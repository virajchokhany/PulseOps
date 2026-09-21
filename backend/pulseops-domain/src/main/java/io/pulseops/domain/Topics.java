package io.pulseops.domain;

/** Kafka topic names. Single source of truth for producers, consumers and the topic bootstrap script. */
public final class Topics {

    public static final String TELEMETRY = "telemetry";
    public static final String ALERTS = "alerts";
    public static final String INCIDENTS = "incidents";

    public static final String TELEMETRY_DLQ = "telemetry.dlq";
    public static final String ALERTS_DLQ = "alerts.dlq";
    public static final String INCIDENTS_DLQ = "incidents.dlq";

    private Topics() {
    }
}
