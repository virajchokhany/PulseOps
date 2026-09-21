package io.pulseops.worker.investigation.llm;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import io.pulseops.domain.alert.AlertEvent;
import io.pulseops.domain.alert.AlertMetric;
import io.pulseops.domain.evidence.CodeSnippet;
import io.pulseops.domain.evidence.DeploymentView;
import io.pulseops.domain.evidence.InvestigationContext;
import io.pulseops.domain.evidence.PullRequestView;
import io.pulseops.domain.evidence.ServiceTelemetrySummary;
import io.pulseops.domain.evidence.ServiceView;
import io.pulseops.domain.rca.RootCauseAnalysis;

/**
 * Produces a root cause analysis by rule, without calling any external model.
 *
 * <p>Active whenever no API key is configured, so the entire pipeline can be demonstrated and
 * tested offline, deterministically, and at no cost. It applies the same reasoning an on-call
 * engineer applies first: what changed recently, does the change plausibly explain the symptoms,
 * and which service is upstream of which.
 *
 * <p>It is not a language model and does not pretend to be. Its conclusions are derived only from
 * values present in the evidence package, its confidence is capped below certainty, and it reports
 * the same evidence gaps that a real model would be instructed to report.
 */
@Component
public class RuleBasedLlmProvider implements LlmProvider {

    private static final long DEPLOYMENT_SUSPICION_WINDOW_MINUTES = 180;
    private static final double MAX_CONFIDENCE = 0.9;

    @Override
    public String name() {
        return "rule-based";
    }

    @Override
    public String model() {
        return "pulseops-heuristic-v1";
    }

    @Override
    public RootCauseAnalysis analyse(InvestigationContext context) {
        Optional<DeploymentView> suspect = suspectedDeployment(context);
        List<PullRequestView> relevantPrs = suspect
                .map(deployment -> context.pullRequests().stream()
                        .filter(pr -> pr.commitSha().equals(deployment.commitSha()))
                        .toList())
                .orElse(List.of());
        List<CodeSnippet> relevantSnippets = suspect
                .map(deployment -> context.codeSnippets().stream()
                        .filter(snippet -> deployment.commitSha().equals(snippet.commitSha()))
                        .toList())
                .orElseGet(context::codeSnippets);

        List<String> evidence = buildEvidence(context, suspect, relevantPrs);
        double confidence = scoreConfidence(context, suspect, relevantPrs, relevantSnippets);

        return new RootCauseAnalysis(
                buildSummary(context),
                buildProbableRootCause(context, suspect, relevantPrs),
                confidence,
                evidence,
                buildTimeline(context, suspect),
                context.affectedServices(),
                suspect.map(RuleBasedLlmProvider::toSuspectedDeployment).orElse(null),
                relevantPrs.stream()
                        .map(pr -> new RootCauseAnalysis.RelevantPullRequest(
                                pr.number(), pr.title(), pr.url(), pr.commitSha(),
                                "Shipped in the deployment that immediately precedes the incident"))
                        .toList(),
                relevantSnippets.stream()
                        .map(snippet -> new RootCauseAnalysis.RelevantCode(
                                snippet.filePath(), snippet.service(),
                                snippet.startLine(), snippet.endLine(), snippet.reason()))
                        .toList(),
                buildRecommendations(context, suspect),
                buildUncertainties(context, suspect, relevantPrs),
                Instant.now(),
                name(),
                model());
    }

    private Optional<DeploymentView> suspectedDeployment(InvestigationContext context) {
        return context.deployments().stream()
                .filter(deployment -> deployment.minutesBeforeIncident() >= 0)
                .filter(deployment -> deployment.minutesBeforeIncident() <= DEPLOYMENT_SUSPICION_WINDOW_MINUTES)
                .min(Comparator.comparingLong(DeploymentView::minutesBeforeIncident));
    }

    private String buildSummary(InvestigationContext context) {
        ServiceTelemetrySummary worst = worstInbound(context).orElse(null);
        String symptom = worst == null
                ? "alerts fired without corroborating telemetry"
                : "%s served %.1f%% server errors with a p95 of %.0fms across %d requests"
                        .formatted(worst.service(), worst.errorRate() * 100,
                                worst.p95LatencyMs(), worst.sampleCount());

        return "%s: %s. %d alert(s) across %s.".formatted(
                context.incident().title(), symptom, context.alerts().size(),
                String.join(", ", context.affectedServices()));
    }

    private String buildProbableRootCause(InvestigationContext context,
                                          Optional<DeploymentView> suspect,
                                          List<PullRequestView> relevantPrs) {
        StringBuilder reason = new StringBuilder();

        if (suspect.isPresent()) {
            DeploymentView deployment = suspect.get();
            reason.append("Hypothesis: deployment %s of %s, which went out %d minute(s) before the incident opened, "
                    .formatted(deployment.version(), deployment.service(), deployment.minutesBeforeIncident()));
            if (!relevantPrs.isEmpty()) {
                PullRequestView pr = relevantPrs.getFirst();
                reason.append("carrying PR #%d (\"%s\"), ".formatted(pr.number(), pr.title()));
            }
            reason.append("introduced the change responsible for the observed behaviour. ");
            reason.append("The timing is correlation, not proof of causation.");
        } else {
            reason.append("Hypothesis: no deployment to the affected services precedes this incident, "
                    + "so the trigger is more likely external or load-related than a code change. ");
        }

        dependencySymptom(context).ifPresent(summary -> reason.append(
                " The calling service's outbound measurements show %s at a p95 of %.0fms, which points downstream "
                        .formatted(String.join(", ", summary.endpoints()), summary.p95LatencyMs())
                        + "rather than at the service itself."));

        upstreamExplanation(context).ifPresent(reason::append);
        return reason.toString();
    }

    /** Explains a dependent service's failures using the dependency graph rather than guessing. */
    private Optional<String> upstreamExplanation(InvestigationContext context) {
        for (ServiceView service : context.services()) {
            for (String dependency : service.dependsOn()) {
                if (context.affectedServices().contains(dependency)) {
                    return Optional.of(" %s depends on %s, so its failures are consistent with being a downstream effect rather than an independent fault."
                            .formatted(service.name(), dependency));
                }
            }
        }
        return Optional.empty();
    }

    private List<String> buildEvidence(InvestigationContext context,
                                       Optional<DeploymentView> suspect,
                                       List<PullRequestView> relevantPrs) {
        List<String> evidence = new ArrayList<>();

        for (ServiceTelemetrySummary summary : context.telemetry()) {
            evidence.add("%s %s: %d samples, %d server errors (%.1f%%), avg %.0fms, p95 %.0fms%s"
                    .formatted(summary.service(), summary.eventType(), summary.sampleCount(),
                            summary.errorCount(), summary.errorRate() * 100,
                            summary.avgLatencyMs(), summary.p95LatencyMs(),
                            summary.endpoints().isEmpty() ? ""
                                    : " on " + String.join(", ", summary.endpoints())));
            for (String message : summary.sampleErrorMessages()) {
                evidence.add("%s reported error: \"%s\"".formatted(summary.service(), message));
            }
        }

        for (AlertEvent alert : context.alerts()) {
            evidence.add("Alert %s on %s: %s was %s against a threshold of %s over %ds (%d samples)"
                    .formatted(alert.alertType(), alert.service(),
                            alert.metric() == null ? "the measured value" : alert.metric().name(),
                            formatMetric(alert.metric(), alert.metricValue()),
                            formatMetric(alert.metric(), alert.threshold()),
                            alert.windowSeconds() == null ? 0 : alert.windowSeconds(),
                            alert.sampleCount() == null ? 0 : alert.sampleCount()));
        }

        suspect.ifPresent(deployment -> evidence.add(
                "Deployment %s of %s (commit %s) completed %d minute(s) before the incident opened"
                        .formatted(deployment.version(), deployment.service(),
                                shortSha(deployment.commitSha()), deployment.minutesBeforeIncident())));

        for (PullRequestView pr : relevantPrs) {
            evidence.add("PR #%d \"%s\" by %s shipped in that deployment and touched: %s"
                    .formatted(pr.number(), pr.title(), pr.author(),
                            String.join(", ", pr.changedFiles())));
        }

        for (ServiceView service : context.services()) {
            if (!service.dependsOn().isEmpty()) {
                evidence.add("Service catalog: %s depends on %s"
                        .formatted(service.name(), String.join(", ", service.dependsOn())));
            }
        }
        return evidence;
    }

    private List<RootCauseAnalysis.TimelinePoint> buildTimeline(InvestigationContext context,
                                                                Optional<DeploymentView> suspect) {
        List<RootCauseAnalysis.TimelinePoint> timeline = new ArrayList<>();

        suspect.ifPresent(deployment -> timeline.add(new RootCauseAnalysis.TimelinePoint(
                deployment.deployedAt(),
                "Deployed %s %s (commit %s)".formatted(deployment.service(), deployment.version(),
                        shortSha(deployment.commitSha())))));

        timeline.add(new RootCauseAnalysis.TimelinePoint(
                context.incident().openedAt(), "Incident opened: " + context.incident().title()));

        for (AlertEvent alert : context.alerts()) {
            timeline.add(new RootCauseAnalysis.TimelinePoint(
                    alert.timestamp(), "%s fired on %s".formatted(alert.alertType(), alert.service())));
        }

        timeline.sort(Comparator.comparing(RootCauseAnalysis.TimelinePoint::at));
        return timeline;
    }

    private List<String> buildRecommendations(InvestigationContext context, Optional<DeploymentView> suspect) {
        List<String> actions = new ArrayList<>();

        suspect.ifPresent(deployment -> actions.add(
                "Consider rolling back %s from %s to the previously deployed version, then confirm whether "
                        .formatted(deployment.service(), deployment.version())
                        + "the error rate and latency recover."));

        dependencySymptom(context).ifPresent(summary -> actions.add(
                "Investigate the health of %s directly; PulseOps only observes it through %s."
                        .formatted(String.join(", ", summary.endpoints()), summary.service())));

        context.runbooks().forEach(runbook -> actions.add(
                "Follow the runbook \"%s\" for %s.".formatted(runbook.title(), runbook.service())));

        if (actions.isEmpty()) {
            actions.add("Gather more evidence before acting; the current package does not support a specific action.");
        }
        actions.add("All recommendations are advisory and require human review before execution.");
        return actions;
    }

    private List<String> buildUncertainties(InvestigationContext context,
                                            Optional<DeploymentView> suspect,
                                            List<PullRequestView> relevantPrs) {
        List<String> uncertainties = new ArrayList<>(context.missingEvidence());

        if (suspect.isPresent()) {
            uncertainties.add("The link to the deployment is temporal only. No evidence here proves the "
                    + "deployed change caused the failures.");
        }
        if (relevantPrs.isEmpty()) {
            uncertainties.add("No pull request could be tied to the suspected change, so the specific "
                    + "code modification is unknown.");
        }
        if (context.alerts().isEmpty()) {
            uncertainties.add("No alerts were attached to this incident, which is unexpected and may "
                    + "indicate an evidence collection problem.");
        }
        return uncertainties;
    }

    private double scoreConfidence(InvestigationContext context,
                                   Optional<DeploymentView> suspect,
                                   List<PullRequestView> relevantPrs,
                                   List<CodeSnippet> snippets) {
        double confidence = 0.25;
        if (suspect.isPresent()) {
            confidence += suspect.get().minutesBeforeIncident() <= 60 ? 0.25 : 0.15;
        }
        if (!relevantPrs.isEmpty()) {
            confidence += 0.2;
        }
        if (!snippets.isEmpty()) {
            confidence += 0.15;
        }
        if (dependencySymptom(context).isPresent()) {
            confidence += 0.1;
        }
        // Heuristics never claim certainty.
        return Math.min(MAX_CONFIDENCE, Math.round(confidence * 100.0) / 100.0);
    }

    private Optional<ServiceTelemetrySummary> worstInbound(InvestigationContext context) {
        return context.telemetry().stream()
                .filter(summary -> "HTTP".equals(summary.eventType()))
                .max(Comparator.comparingDouble(ServiceTelemetrySummary::errorRate));
    }

    /**
     * The most informative outbound symptom.
     *
     * <p>Prefers the primary service's own dependency calls. A downstream service's call into the
     * primary service is also slow, but saying so merely restates the incident; what advances the
     * investigation is what the <em>primary</em> service was itself waiting on.
     */
    private Optional<ServiceTelemetrySummary> dependencySymptom(InvestigationContext context) {
        List<ServiceTelemetrySummary> candidates = context.telemetry().stream()
                .filter(summary -> "DEPENDENCY".equals(summary.eventType()))
                .filter(summary -> summary.errorRate() > 0 || summary.p95LatencyMs() > 1000)
                .toList();

        String primary = context.incident().primaryService();
        return candidates.stream()
                .filter(summary -> summary.service().equals(primary))
                .max(Comparator.comparingDouble(ServiceTelemetrySummary::p95LatencyMs))
                .or(() -> candidates.stream()
                        .max(Comparator.comparingDouble(ServiceTelemetrySummary::p95LatencyMs)));
    }

    private static RootCauseAnalysis.SuspectedDeployment toSuspectedDeployment(DeploymentView deployment) {
        return new RootCauseAnalysis.SuspectedDeployment(
                deployment.service(), deployment.version(), deployment.commitSha(),
                deployment.deployedAt(), deployment.minutesBeforeIncident(),
                "Most recent deployment to an affected service before the incident opened"
                        + (deployment.notes() == null ? "" : "; release note: " + deployment.notes()));
    }

    /**
     * Formats a metric value with the right unit, and with <em>no</em> unit when the metric is
     * unknown. Guessing wrong turns an error rate of 0.25 into "0ms", which reads as healthy.
     */
    private static String formatMetric(AlertMetric metric, Double value) {
        if (value == null) {
            return "unknown";
        }
        if (metric == null) {
            return "%s".formatted(value);
        }
        return metric == AlertMetric.ERROR_RATE
                ? "%.1f%%".formatted(value * 100)
                : "%.0fms".formatted(value);
    }

    private static String shortSha(String sha) {
        return sha == null || sha.length() < 8 ? sha : sha.substring(0, 8);
    }
}
