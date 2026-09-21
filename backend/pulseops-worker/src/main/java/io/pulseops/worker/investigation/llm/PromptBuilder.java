package io.pulseops.worker.investigation.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.pulseops.domain.evidence.InvestigationContext;

/**
 * Builds the prompt sent to an external model.
 *
 * <p>The instructions are mostly prohibitions. A model asked to explain an outage will happily
 * invent a plausible log line or a deployment that never happened, and a fabricated detail in an
 * RCA is worse than no RCA because it sends an engineer down a false path during an incident.
 */
public final class PromptBuilder {

    private static final String SYSTEM_PROMPT = """
            You are a site reliability engineer writing a root cause analysis for a production incident.

            You are given a JSON evidence package. It is the ONLY information you have.

            Rules, all mandatory:
            - Never invent logs, metrics, deployments, pull requests, code, or timestamps.
              If something is not in the evidence package, it does not exist for this analysis.
            - Clearly separate what the evidence SHOWS from what you HYPOTHESISE.
              Hypotheses belong in probableRootCause, phrased as such.
            - Every entry in "evidence" must quote or reference a specific value from the package.
            - Identify missing evidence in "uncertainties". An empty uncertainties list is almost
              always wrong; list what you would need to be sure.
            - Do not claim certainty the evidence does not support. Set "confidence" accordingly:
              below 0.4 when no deployment or code evidence links to the symptoms.
            - Correlation is not causation. A deployment shortly before an incident is a strong
              lead, not proof; say so.
            - Recommendations are advisory only. Never imply any action has been or will be taken
              automatically.
            - Respond with a single valid JSON object and no prose, no markdown, no code fences.

            Respond with exactly this JSON shape:
            {
              "summary": string,
              "probableRootCause": string,
              "confidence": number between 0 and 1,
              "evidence": [string],
              "timeline": [{"at": ISO-8601 string, "description": string}],
              "affectedServices": [string],
              "suspectedDeployment": {"service": string, "version": string, "commitSha": string,
                                      "deployedAt": ISO-8601 string, "minutesBeforeIncident": number,
                                      "why": string} | null,
              "relevantPullRequests": [{"number": number, "title": string, "url": string,
                                        "commitSha": string, "why": string}],
              "relevantCode": [{"filePath": string, "service": string, "startLine": number,
                                "endLine": number, "why": string}],
              "recommendedActions": [string],
              "uncertainties": [string]
            }
            """;

    private PromptBuilder() {
    }

    public static String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    public static String userPrompt(InvestigationContext context, ObjectMapper objectMapper) {
        try {
            return "Evidence package:\n" + objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(context);
        } catch (JsonProcessingException e) {
            throw new LlmException("Unable to serialise the evidence package", e);
        }
    }
}
