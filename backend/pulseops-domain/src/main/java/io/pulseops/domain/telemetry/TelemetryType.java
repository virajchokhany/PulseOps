package io.pulseops.domain.telemetry;

public enum TelemetryType {

    /** An inbound HTTP request served by the emitting service. */
    HTTP,

    /** An outbound call to another service or an external provider. */
    DEPENDENCY,

    /** A domain-level outcome, e.g. "order rejected". */
    BUSINESS
}
