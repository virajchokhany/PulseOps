package io.pulseops.worker.investigation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.pulseops.domain.Severity;
import io.pulseops.domain.alert.AlertEvent;
import io.pulseops.domain.alert.AlertMetric;
import io.pulseops.domain.evidence.CodeSnippet;
import io.pulseops.domain.evidence.DeploymentView;
import io.pulseops.domain.evidence.InvestigationContext;
import io.pulseops.domain.evidence.PullRequestView;
import io.pulseops.domain.evidence.RunbookView;
import io.pulseops.domain.evidence.ServiceTelemetrySummary;
import io.pulseops.domain.evidence.ServiceView;
import io.pulseops.infrastructure.alert.AlertEntity;
import io.pulseops.infrastructure.alert.AlertRepository;
import io.pulseops.infrastructure.incident.IncidentEntity;
import io.pulseops.worker.investigation.evidence.DeploymentEvidenceProvider;
import io.pulseops.worker.investigation.evidence.EvidenceScope;
import io.pulseops.worker.investigation.evidence.PullRequestEvidenceProvider;
import io.pulseops.worker.investigation.evidence.RunbookEvidenceProvider;
import io.pulseops.worker.investigation.evidence.ServiceCatalogEvidenceProvider;
import io.pulseops.worker.investigation.evidence.SourceCodeEvidenceProvider;
import io.pulseops.worker.investigation.evidence.TelemetryEvidenceProvider;

/**
 * Orchestrates the evidence providers into a single {@link InvestigationContext}.
 *
 * <p>Also records what could <em>not</em> be gathered. Stating the gaps explicitly is what stops an
 * analysis quietly treating absent evidence as absence of a problem, and gives the model something
 * concrete to put in its uncertainties.
 */
@Component
@EnableConfigurationProperties(InvestigationProperties.class)
public class EvidenceCollector {

    private static final Logger log = LoggerFactory.getLogger(EvidenceCollector.class);

    private final InvestigationProperties properties;
    private final AlertRepository alertRepository;
    private final TelemetryEvidenceProvider telemetryProvider;
    private final DeploymentEvidenceProvider deploymentProvider;
    private final PullRequestEvidenceProvider pullRequestProvider;
    private final ServiceCatalogEvidenceProvider serviceProvider;
    private final SourceCodeEvidenceProvider sourceCodeProvider;
    private final RunbookEvidenceProvider runbookProvider;

    public EvidenceCollector(InvestigationProperties properties,
                             AlertRepository alertRepository,
                             TelemetryEvidenceProvider telemetryProvider,
                             DeploymentEvidenceProvider deploymentProvider,
                             PullRequestEvidenceProvider pullRequestProvider,
                             ServiceCatalogEvidenceProvider serviceProvider,
                             SourceCodeEvidenceProvider sourceCodeProvider,
                             RunbookEvidenceProvider runbookProvider) {
        this.properties = properties;
        this.alertRepository = alertRepository;
        this.telemetryProvider = telemetryProvider;
        this.deploymentProvider = deploymentProvider;
        this.pullRequestProvider = pullRequestProvider;
        this.serviceProvider = serviceProvider;
        this.sourceCodeProvider = sourceCodeProvider;
        this.runbookProvider = runbookProvider;
    }

    @Transactional(readOnly = true)
    public InvestigationContext collect(IncidentEntity incident) {
        List<AlertEntity> alertEntities =
                alertRepository.findByIncidentKeyOrderByOccurredAtAsc(incident.getIncidentKey());
        List<AlertEvent> alerts = alertEntities.stream().map(EvidenceCollector::toAlertEvent).toList();

        List<String> affectedServices = new ArrayList<>(incident.getAffectedServices());
        EvidenceScope scope = new EvidenceScope(
                incident.getIncidentKey(),
                incident.getPrimaryService(),
                affectedServices,
                incident.getOpenedAt(),
                incident.getOpenedAt().minus(properties.getTelemetryWindowBefore()),
                Instant.now(),
                alertEntities.stream().map(AlertEntity::getAlertType).distinct().toList());

        List<ServiceTelemetrySummary> telemetry = telemetryProvider.collect(scope);
        List<DeploymentView> deployments = deploymentProvider.collect(scope);
        List<PullRequestView> pullRequests = pullRequestProvider.collect(scope);
        List<ServiceView> services = serviceProvider.collect(scope);
        List<CodeSnippet> codeSnippets = sourceCodeProvider.collect(scope);
        List<RunbookView> runbooks = runbookProvider.collect(scope);

        List<String> missing = describeGaps(telemetry, deployments, pullRequests, codeSnippets, runbooks);

        log.info("Collected evidence for incident {}: {} alerts, {} telemetry groups, {} deployments, "
                        + "{} PRs, {} snippets, {} runbooks, {} gaps",
                incident.getIncidentKey(), alerts.size(), telemetry.size(), deployments.size(),
                pullRequests.size(), codeSnippets.size(), runbooks.size(), missing.size());

        return new InvestigationContext(
                new InvestigationContext.IncidentView(
                        incident.getIncidentKey(), incident.getIncidentNumber(), incident.getTitle(),
                        incident.getStatus().name(), incident.getSeverity(), incident.getPrimaryService(),
                        incident.getAlertCount(), incident.getVersion(),
                        incident.getOpenedAt(), incident.getUpdatedAt()),
                affectedServices, alerts, telemetry, services, deployments,
                pullRequests, codeSnippets, runbooks, missing);
    }

    private List<String> describeGaps(List<ServiceTelemetrySummary> telemetry,
                                      List<DeploymentView> deployments,
                                      List<PullRequestView> pullRequests,
                                      List<CodeSnippet> codeSnippets,
                                      List<RunbookView> runbooks) {
        List<String> missing = new ArrayList<>();
        if (telemetry.isEmpty()) {
            missing.add("No telemetry was found in the incident window.");
        }
        if (deployments.isEmpty()) {
            missing.add("No deployments to the affected services in the 24 hours before the incident.");
        }
        if (pullRequests.isEmpty()) {
            missing.add("No pull requests could be linked to recent deployments.");
        }
        if (codeSnippets.isEmpty()) {
            missing.add("No source code was retrieved; conclusions about code are not evidence-backed.");
        }
        if (runbooks.isEmpty()) {
            missing.add("No runbook exists for the services and alert types involved.");
        }

        // Always true in this architecture and worth stating: PulseOps only sees ShopFlow's side of
        // an external call, never the provider's own internals.
        boolean hasDependencyTelemetry = telemetry.stream()
                .anyMatch(summary -> "DEPENDENCY".equals(summary.eventType()));
        if (hasDependencyTelemetry) {
            missing.add("No telemetry from external providers themselves; their behaviour is inferred "
                    + "only from the calling service's outbound measurements.");
        }
        return missing;
    }

    private static AlertEvent toAlertEvent(AlertEntity entity) {
        return new AlertEvent(
                entity.getAlertId(), entity.getOccurredAt(), entity.getService(), entity.getAlertType(),
                Severity.valueOf(entity.getSeverity()), entity.getDescription(),
                entity.getMetric() != null ? AlertMetric.valueOf(entity.getMetric()) : null,
                entity.getMetricValue(), entity.getThreshold(), entity.getWindowSeconds(),
                entity.getSampleCount(), entity.getCorrelationId());
    }
}
