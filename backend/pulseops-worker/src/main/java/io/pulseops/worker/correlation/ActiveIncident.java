package io.pulseops.worker.correlation;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import io.pulseops.domain.Severity;

/** Correlation's own working state for one open incident. Mutated only under the engine's lock. */
class ActiveIncident {

    private final UUID incidentKey;
    private final Instant openedAt;
    private final String correlationId;

    private String title;
    private Severity severity;
    private String primaryService;
    private final Set<String> affectedServices = new LinkedHashSet<>();
    private Instant lastAlertAt;
    private int alertCount;
    private long version;

    ActiveIncident(UUID incidentKey, String title, Severity severity, String primaryService,
                   Instant openedAt, String correlationId) {
        this.incidentKey = incidentKey;
        this.title = title;
        this.severity = severity;
        this.primaryService = primaryService;
        this.openedAt = openedAt;
        this.lastAlertAt = openedAt;
        this.correlationId = correlationId;
        this.affectedServices.add(primaryService);
        this.alertCount = 1;
        this.version = 1;
    }

    boolean involves(String service) {
        return affectedServices.contains(service);
    }

    void addService(String service) {
        affectedServices.add(service);
    }

    void escalateTo(Severity candidate) {
        if (candidate != null && candidate.ordinal() > severity.ordinal()) {
            severity = candidate;
        }
    }

    void promotePrimary(String service, String newTitle) {
        this.primaryService = service;
        this.title = newTitle;
    }

    long recordAlert(Instant at) {
        this.lastAlertAt = at;
        this.alertCount++;
        return ++this.version;
    }

    UUID incidentKey() {
        return incidentKey;
    }

    String title() {
        return title;
    }

    Severity severity() {
        return severity;
    }

    String primaryService() {
        return primaryService;
    }

    Set<String> affectedServices() {
        return affectedServices;
    }

    Instant openedAt() {
        return openedAt;
    }

    Instant lastAlertAt() {
        return lastAlertAt;
    }

    int alertCount() {
        return alertCount;
    }

    long version() {
        return version;
    }

    String correlationId() {
        return correlationId;
    }
}
