package io.pulseops.infrastructure.incident;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "incident_timeline")
public class IncidentTimelineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "incident_key", nullable = false)
    private UUID incidentKey;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "entry_type", nullable = false, length = 50)
    private String entryType;

    @Column(name = "summary", nullable = false)
    private String summary;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected IncidentTimelineEntity() {
    }

    public IncidentTimelineEntity(UUID incidentKey, Instant occurredAt, String entryType,
                                  String summary, String correlationId) {
        this.incidentKey = incidentKey;
        this.occurredAt = occurredAt;
        this.entryType = entryType;
        this.summary = summary;
        this.correlationId = correlationId;
    }

    public Long getId() {
        return id;
    }

    public UUID getIncidentKey() {
        return incidentKey;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getEntryType() {
        return entryType;
    }

    public String getSummary() {
        return summary;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
