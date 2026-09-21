package io.pulseops.domain.incident;

public enum IncidentEventType {

    CREATED,

    /** A new correlated alert changed an existing incident. Triggers a fresh AI investigation. */
    UPDATED,

    RESOLVED
}
