package io.pulseops.infrastructure.telemetry;

import java.time.Instant;

import io.pulseops.domain.telemetry.TelemetryEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "telemetry_events")
public class TelemetryEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "event_id", nullable = false, length = 100, unique = true)
    private String eventId;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "trace_id", length = 100)
    private String traceId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "service", nullable = false, length = 100)
    private String service;

    @Column(name = "environment", nullable = false, length = 50)
    private String environment;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "endpoint", length = 255)
    private String endpoint;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "message")
    private String message;

    @Column(name = "deployment_version", length = 100)
    private String deploymentVersion;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    protected TelemetryEventEntity() {
    }

    public static TelemetryEventEntity from(TelemetryEvent event) {
        TelemetryEventEntity entity = new TelemetryEventEntity();
        entity.eventId = event.eventId();
        entity.correlationId = event.correlationId();
        entity.traceId = event.traceId();
        entity.occurredAt = event.timestamp();
        entity.service = event.service();
        entity.environment = event.environment();
        entity.eventType = event.type().name();
        entity.endpoint = truncate(event.endpoint(), 255);
        entity.statusCode = event.statusCode();
        entity.latencyMs = event.latencyMs();
        entity.message = event.message();
        entity.deploymentVersion = truncate(event.deploymentVersion(), 100);
        return entity;
    }

    /** Guards against an over-long field turning a telemetry event into a permanent insert failure. */
    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
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

    public String getEnvironment() {
        return environment;
    }

    public String getEventType() {
        return eventType;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public String getMessage() {
        return message;
    }

    public String getDeploymentVersion() {
        return deploymentVersion;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
