package io.pulseops.domain.evidence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.pulseops.domain.Severity;
import io.pulseops.domain.alert.AlertEvent;

/**
 * The evidence package handed to the LLM.
 *
 * <p>This is the central safety property of PulseOps. The model never queries PostgreSQL, never
 * reads Kafka and never touches source control. The platform gathers a bounded set of relevant
 * facts first, and the model reasons only over what is in this object. That keeps the model's
 * inputs auditable: whatever it concluded, we can show exactly what it was given.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InvestigationContext(
        IncidentView incident,
        List<String> affectedServices,
        List<AlertEvent> alerts,
        List<ServiceTelemetrySummary> telemetry,
        List<ServiceView> services,
        List<DeploymentView> deployments,
        List<PullRequestView> pullRequests,
        List<CodeSnippet> codeSnippets,
        List<RunbookView> runbooks,
        List<String> missingEvidence) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IncidentView(
            UUID incidentKey,
            Long incidentNumber,
            String title,
            String status,
            Severity severity,
            String primaryService,
            int alertCount,
            long version,
            Instant openedAt,
            Instant updatedAt) {
    }
}
