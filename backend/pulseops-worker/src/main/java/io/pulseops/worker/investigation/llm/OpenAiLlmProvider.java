package io.pulseops.worker.investigation.llm;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.pulseops.domain.evidence.InvestigationContext;
import io.pulseops.domain.rca.RootCauseAnalysis;

/**
 * Calls an OpenAI-compatible chat completions endpoint.
 *
 * <p>The call is bounded by a connect and read timeout, and the response is parsed into the
 * structured RCA type and validated before it is allowed anywhere near the database. A model that
 * returns prose, malformed JSON or an empty analysis is treated as a failure rather than persisted
 * as a result.
 */
public class OpenAiLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmProvider.class);

    private final LlmProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiLlmProvider(LlmProperties properties, RestClient restClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public String model() {
        return properties.getModel();
    }

    @Override
    public RootCauseAnalysis analyse(InvestigationContext context) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.getModel());
        // Asking for a JSON object removes an entire class of parse failures.
        request.put("response_format", Map.of("type", "json_object"));
        request.put("messages", List.of(
                Map.of("role", "system", "content", PromptBuilder.systemPrompt()),
                Map.of("role", "user", "content", PromptBuilder.userPrompt(context, objectMapper))));

        if (properties.isReasoningModel()) {
            // Temperature is deliberately omitted: these models only accept the default.
            request.put("max_completion_tokens", properties.effectiveMaxOutputTokens());
        } else {
            request.put("temperature", properties.getTemperature());
            request.put("max_tokens", properties.effectiveMaxOutputTokens());
        }

        String content = callModel(request);
        RootCauseAnalysis analysis = parse(content);
        validate(analysis);
        return analysis.withProvenance(name(), properties.getModel(), Instant.now());
    }

    private String callModel(Map<String, Object> request) {
        try {
            // Read the raw body rather than letting RestClient negotiate a converter. Azure has
            // been observed replying with application/octet-stream, which fails content-type
            // matching even though the payload is perfectly good JSON.
            String raw = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);

            if (raw == null || raw.isBlank()) {
                throw new LlmException("Model returned an empty response body");
            }

            OpenAiResponse response;
            try {
                response = objectMapper.readValue(raw, OpenAiResponse.class);
            } catch (Exception e) {
                throw new LlmException("Model response envelope was not valid JSON", e);
            }

            if (response.choices() == null || response.choices().isEmpty()) {
                throw new LlmException("Model returned no choices");
            }

            OpenAiResponse.Choice choice = response.choices().getFirst();
            // Without this check, a truncated response surfaces as an unhelpful JSON parse error.
            if ("length".equals(choice.finishReason())) {
                throw new LlmException("Model response was truncated at the token limit ("
                        + properties.effectiveMaxOutputTokens() + "). Raise "
                        + (properties.isReasoningModel()
                                ? "pulseops.llm.reasoning-max-output-tokens"
                                : "pulseops.llm.max-output-tokens") + ".");
            }

            String content = choice.message() == null ? null : choice.message().content();
            if (content == null || content.isBlank()) {
                // Reasoning models return empty content when the whole budget went on reasoning.
                throw new LlmException("Model returned empty content"
                        + (response.usage() == null ? ""
                                : " (reasoning tokens: " + response.usage().reasoningTokens() + ")"));
            }

            if (log.isDebugEnabled() && response.usage() != null) {
                log.debug("Model usage: completion={} reasoning={}",
                        response.usage().completionTokens(), response.usage().reasoningTokens());
            }
            return content;
        } catch (RestClientResponseException e) {
            // The body can echo the prompt, so only the status is logged. The Azure error body is
            // the fastest way to diagnose a 400 or 404, so its first line is included verbatim.
            String hint = e.getResponseBodyAsString();
            if (hint != null && hint.length() > 300) {
                hint = hint.substring(0, 300);
            }
            throw new LlmException("Model call failed with HTTP " + e.getStatusCode().value()
                    + (hint == null || hint.isBlank() ? "" : ": " + hint), e);
        } catch (ResourceAccessException e) {
            throw new LlmException("Model call timed out or was unreachable: " + e.getMessage(), e);
        }
    }

    private RootCauseAnalysis parse(String content) {
        try {
            return objectMapper.readValue(stripCodeFence(content), RootCauseAnalysis.class);
        } catch (Exception e) {
            log.warn("Model returned unparseable content ({} chars)", content == null ? 0 : content.length());
            throw new LlmException("Model response was not valid RootCauseAnalysis JSON", e);
        }
    }

    /** Models still wrap JSON in a fence occasionally, even when told not to. */
    private static String stripCodeFence(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private static void validate(RootCauseAnalysis analysis) {
        if (analysis.summary() == null || analysis.summary().isBlank()) {
            throw new LlmException("Model response has no summary");
        }
        if (analysis.probableRootCause() == null || analysis.probableRootCause().isBlank()) {
            throw new LlmException("Model response has no probableRootCause");
        }
        if (analysis.confidence() == null || analysis.confidence() < 0 || analysis.confidence() > 1) {
            throw new LlmException("Model response has an out-of-range confidence: " + analysis.confidence());
        }
        if (analysis.evidence() == null || analysis.evidence().isEmpty()) {
            throw new LlmException("Model response cites no evidence");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiResponse(List<Choice> choices, Usage usage) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Message(String content) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Usage(
                @JsonProperty("completion_tokens") Integer completionTokens,
                @JsonProperty("completion_tokens_details") Details details) {

            Integer reasoningTokens() {
                return details == null ? null : details.reasoningTokens();
            }

            @JsonIgnoreProperties(ignoreUnknown = true)
            private record Details(@JsonProperty("reasoning_tokens") Integer reasoningTokens) {
            }
        }
    }
}
