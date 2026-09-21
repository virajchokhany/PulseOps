package io.pulseops.infrastructure.investigation;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One investigation attempt.
 *
 * <p>Every attempt is a row, including failures. Overwriting a single RCA per incident would hide
 * both the history of how understanding evolved and the fact that an investigation failed at all.
 */
@Entity
@Table(name = "ai_investigations")
public class AiInvestigationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "incident_key", nullable = false)
    private UUID incidentKey;

    /** Incident version this RCA was computed from; if the incident moved on, the RCA is stale. */
    @Column(name = "incident_version", nullable = false)
    private long incidentVersion;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "provider", length = 50)
    private String provider;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "attempt", nullable = false)
    private int attempt = 1;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "prompt_chars")
    private Integer promptChars;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rca")
    private String rca;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_summary")
    private String evidenceSummary;

    protected AiInvestigationEntity() {
    }

    public AiInvestigationEntity(UUID incidentKey, long incidentVersion, String status,
                                 int attempt, String correlationId) {
        this.incidentKey = incidentKey;
        this.incidentVersion = incidentVersion;
        this.status = status;
        this.attempt = attempt;
        this.correlationId = correlationId;
    }

    public void succeeded(String provider, String model, String rcaJson, String evidenceSummaryJson,
                          long durationMs, int promptChars) {
        this.status = "COMPLETED";
        this.provider = provider;
        this.model = model;
        this.rca = rcaJson;
        this.evidenceSummary = evidenceSummaryJson;
        this.durationMs = durationMs;
        this.promptChars = promptChars;
        this.completedAt = Instant.now();
    }

    public void failed(String reason, long durationMs) {
        this.status = "FAILED";
        this.failureReason = reason;
        this.durationMs = durationMs;
        this.completedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getIncidentKey() {
        return incidentKey;
    }

    public long getIncidentVersion() {
        return incidentVersion;
    }

    public String getStatus() {
        return status;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public int getAttempt() {
        return attempt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getRca() {
        return rca;
    }

    public String getEvidenceSummary() {
        return evidenceSummary;
    }
}
