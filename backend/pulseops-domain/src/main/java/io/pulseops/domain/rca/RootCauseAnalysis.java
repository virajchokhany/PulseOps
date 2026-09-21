package io.pulseops.domain.rca;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The structured root cause analysis produced from an evidence package.
 *
 * <p>Structured rather than prose so the dashboard can render sections, so fields can be validated
 * before persisting, and so a model that omits its uncertainties is detectably wrong rather than
 * merely unhelpful.
 *
 * @param confidence     0.0-1.0, and expected to be low when the evidence is thin
 * @param evidence       statements that cite something actually present in the context
 * @param uncertainties  what the analysis could not establish; empty is a red flag, not a success
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RootCauseAnalysis(
        String summary,
        String probableRootCause,
        Double confidence,
        List<String> evidence,
        List<TimelinePoint> timeline,
        List<String> affectedServices,
        SuspectedDeployment suspectedDeployment,
        List<RelevantPullRequest> relevantPullRequests,
        List<RelevantCode> relevantCode,
        List<String> recommendedActions,
        List<String> uncertainties,
        Instant generatedAt,
        String provider,
        String model) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TimelinePoint(Instant at, String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SuspectedDeployment(
            String service,
            String version,
            String commitSha,
            Instant deployedAt,
            long minutesBeforeIncident,
            String why) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RelevantPullRequest(int number, String title, String url, String commitSha, String why) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RelevantCode(String filePath, String service, int startLine, int endLine, String why) {
    }

    public RootCauseAnalysis withProvenance(String provider, String model, Instant generatedAt) {
        return new RootCauseAnalysis(summary, probableRootCause, confidence, evidence, timeline,
                affectedServices, suspectedDeployment, relevantPullRequests, relevantCode,
                recommendedActions, uncertainties, generatedAt, provider, model);
    }
}
