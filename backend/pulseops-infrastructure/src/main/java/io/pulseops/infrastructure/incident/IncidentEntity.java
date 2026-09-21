package io.pulseops.infrastructure.incident;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import io.pulseops.domain.IncidentStatus;
import io.pulseops.domain.RcaStatus;
import io.pulseops.domain.Severity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "incidents")
/*
 * Two services write this row: the API projects correlation state (title, severity, alert_count,
 * version) and the worker owns the RCA columns. Hibernate's default UPDATE writes every column, so
 * whichever service flushed last silently reverted the other's fields from its own stale snapshot.
 * That was observed as an incident showing 3 correlated alerts while 4 alert rows pointed at it.
 */
@DynamicUpdate
public class IncidentEntity {

    @Id
    @Column(name = "incident_key", nullable = false)
    private UUID incidentKey;

    /** Assigned by a database sequence purely for human-readable references like "Incident #1042". */
    @Generated(event = EventType.INSERT)
    @Column(name = "incident_number", insertable = false, updatable = false)
    private Long incidentNumber;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IncidentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private Severity severity;

    @Column(name = "primary_service", nullable = false, length = 100)
    private String primaryService;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "alert_count", nullable = false)
    private int alertCount;

    @Column(name = "version", nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "rca_status", nullable = false, length = 20)
    private RcaStatus rcaStatus = RcaStatus.NOT_STARTED;

    @Column(name = "current_rca_id")
    private Long currentRcaId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "incident_services", joinColumns = @JoinColumn(name = "incident_key"))
    @Column(name = "service_name", nullable = false, length = 100)
    private Set<String> affectedServices = new LinkedHashSet<>();

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected IncidentEntity() {
    }

    public IncidentEntity(UUID incidentKey, String title, Severity severity, String primaryService,
                          Set<String> affectedServices, String correlationId, Instant openedAt, long version) {
        this.incidentKey = incidentKey;
        this.title = title;
        this.status = IncidentStatus.OPEN;
        this.severity = severity;
        this.primaryService = primaryService;
        this.affectedServices = new LinkedHashSet<>(affectedServices);
        this.correlationId = correlationId;
        this.openedAt = openedAt;
        this.updatedAt = openedAt;
        this.alertCount = 1;
        this.version = version;
    }

    public void apply(String title, Severity severity, String primaryService, Set<String> affectedServices,
                      int alertCount, long version, Instant updatedAt) {
        this.title = title;
        this.severity = severity;
        this.primaryService = primaryService;
        this.affectedServices = new LinkedHashSet<>(affectedServices);
        this.alertCount = alertCount;
        this.version = version;
        this.updatedAt = updatedAt;
    }

    /**
     * A completed RCA becomes STALE rather than being cleared, so an operator keeps reading the
     * previous conclusion while a fresh investigation runs.
     */
    public void markRcaStale() {
        if (rcaStatus == RcaStatus.COMPLETED) {
            this.rcaStatus = RcaStatus.STALE;
        }
    }

    public void setRcaStatus(RcaStatus rcaStatus) {
        this.rcaStatus = rcaStatus;
    }

    public void setCurrentRcaId(Long currentRcaId) {
        this.currentRcaId = currentRcaId;
    }

    public void resolve(Instant resolvedAt) {
        this.status = IncidentStatus.RESOLVED;
        this.resolvedAt = resolvedAt;
        this.updatedAt = resolvedAt;
    }

    public UUID getIncidentKey() {
        return incidentKey;
    }

    public Long getIncidentNumber() {
        return incidentNumber;
    }

    public String getTitle() {
        return title;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getPrimaryService() {
        return primaryService;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public int getAlertCount() {
        return alertCount;
    }

    public long getVersion() {
        return version;
    }

    public RcaStatus getRcaStatus() {
        return rcaStatus;
    }

    public Long getCurrentRcaId() {
        return currentRcaId;
    }

    public Set<String> getAffectedServices() {
        return affectedServices;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
