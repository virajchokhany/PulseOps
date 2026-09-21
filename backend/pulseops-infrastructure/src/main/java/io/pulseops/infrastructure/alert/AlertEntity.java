package io.pulseops.infrastructure.alert;

import java.time.Instant;
import java.util.UUID;

import io.pulseops.domain.alert.AlertEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "alerts")
public class AlertEntity {

    @Id
    @Column(name = "alert_id", nullable = false, length = 100)
    private String alertId;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "service", nullable = false, length = 100)
    private String service;

    @Column(name = "alert_type", nullable = false, length = 100)
    private String alertType;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "metric", length = 30)
    private String metric;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "metric_value")
    private Double metricValue;

    @Column(name = "threshold")
    private Double threshold;

    @Column(name = "window_seconds")
    private Integer windowSeconds;

    @Column(name = "sample_count")
    private Integer sampleCount;

    /** Set by the Incident Store once correlation has decided which incident owns this alert. */
    @Column(name = "incident_key")
    private UUID incidentKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AlertEntity() {
    }

    public static AlertEntity from(AlertEvent event) {
        AlertEntity entity = new AlertEntity();
        entity.alertId = event.alertId();
        entity.correlationId = event.correlationId();
        entity.occurredAt = event.timestamp();
        entity.service = event.service();
        entity.alertType = event.alertType();
        entity.severity = event.severity().name();
        entity.metric = event.metric() != null ? event.metric().name() : null;
        entity.description = event.description();
        entity.metricValue = event.metricValue();
        entity.threshold = event.threshold();
        entity.windowSeconds = event.windowSeconds();
        entity.sampleCount = event.sampleCount();
        return entity;
    }

    public void assignToIncident(UUID incidentKey) {
        this.incidentKey = incidentKey;
    }

    public String getAlertId() {
        return alertId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getService() {
        return service;
    }

    public String getAlertType() {
        return alertType;
    }

    public String getSeverity() {
        return severity;
    }

    public String getMetric() {
        return metric;
    }

    public String getDescription() {
        return description;
    }

    public Double getMetricValue() {
        return metricValue;
    }

    public Double getThreshold() {
        return threshold;
    }

    public Integer getWindowSeconds() {
        return windowSeconds;
    }

    public Integer getSampleCount() {
        return sampleCount;
    }

    public UUID getIncidentKey() {
        return incidentKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
