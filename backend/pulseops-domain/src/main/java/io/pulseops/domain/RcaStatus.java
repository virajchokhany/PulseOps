package io.pulseops.domain;

/**
 * Lifecycle of the automated root cause analysis attached to an incident.
 *
 * <p>{@link #STALE} exists because incidents keep changing after the first investigation. A new
 * alert invalidates the current RCA without deleting it: the operator keeps reading the previous
 * conclusion while the worker computes an updated one.
 */
public enum RcaStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    STALE,
    FAILED
}
