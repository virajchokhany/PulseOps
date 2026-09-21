package io.pulseops.domain.alert;

/** The measurable quantities an alert rule can threshold. Deterministic arithmetic, no ML. */
public enum AlertMetric {

    /** Fraction of HTTP samples in the window whose status was 5xx, expressed 0.0-1.0. */
    ERROR_RATE,

    AVG_LATENCY_MS,

    /**
     * 95th percentile latency. Preferred over the average for latency alerting: an average is
     * easily hidden by fast requests, while a p95 reflects what a bad-but-not-rare request looks
     * like.
     */
    P95_LATENCY_MS
}
