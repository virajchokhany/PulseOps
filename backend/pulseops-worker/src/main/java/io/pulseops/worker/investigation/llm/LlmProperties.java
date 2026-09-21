package io.pulseops.worker.investigation.llm;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulseops.llm")
public class LlmProperties {

    /** Blank selects the deterministic local provider. No key means no outbound call. */
    private String apiKey = "";

    /**
     * How the key is presented: {@code bearer} sends {@code Authorization: Bearer <key>} (OpenAI,
     * and the Azure v1 endpoint via the OpenAI SDKs); {@code api-key} sends {@code api-key: <key>}
     * (Azure's own REST examples). Both are accepted by Azure OpenAI, so this exists purely so a
     * 401 can be resolved by configuration instead of a code change.
     */
    private String authHeader = "bearer";

    private String model = "gpt-4.1-mini";

    private String baseUrl = "https://api.openai.com/v1";

    /**
     * Hard bound on the external call. An incident investigation that hangs is worse than one that
     * fails: the RCA would sit at IN_PROGRESS indefinitely while an operator waits for it.
     *
     * <p>Reasoning models are slow. A single RCA over a full evidence package was measured at
     * roughly 45 seconds, so the default leaves real headroom above that.
     */
    private Duration timeout = Duration.ofSeconds(120);

    /**
     * Budget for non-reasoning models. A measured RCA emitted roughly 2,650 visible tokens, so the
     * obvious-looking 2,000 would have truncated it.
     */
    private int maxOutputTokens = 4000;

    /**
     * Budget for reasoning models, which spend most of it on internal reasoning tokens before
     * emitting any visible output. Measured against this deployment, a trivial prompt used 142
     * completion tokens of which 128 were reasoning, so the visible RCA needs far more headroom
     * than a non-reasoning model would.
     */
    private int reasoningMaxOutputTokens = 8000;

    /** Token budget appropriate to the configured model. */
    public int effectiveMaxOutputTokens() {
        return isReasoningModel() ? reasoningMaxOutputTokens : maxOutputTokens;
    }

    public int getReasoningMaxOutputTokens() {
        return reasoningMaxOutputTokens;
    }

    public void setReasoningMaxOutputTokens(int reasoningMaxOutputTokens) {
        this.reasoningMaxOutputTokens = reasoningMaxOutputTokens;
    }

    /** Low, because the task is evidence summarisation and not creative writing. */
    private double temperature = 0.1;

    /**
     * Newer reasoning models (the GPT-5 family and o-series) reject {@code max_tokens} in favour of
     * {@code max_completion_tokens}, and reject any temperature other than the default. Sending the
     * older parameters to them fails with a 400 that reads like a malformed request rather than an
     * unsupported option. Left unset, this is inferred from the model name.
     */
    private Boolean reasoningModel;

    /** True when the configured model needs the reasoning-style parameter set. */
    public boolean isReasoningModel() {
        if (reasoningModel != null) {
            return reasoningModel;
        }
        String name = model == null ? "" : model.toLowerCase(java.util.Locale.ROOT);
        return name.startsWith("gpt-5") || name.startsWith("gpt-6")
                || name.startsWith("o1") || name.startsWith("o3") || name.startsWith("o4");
    }

    public void setReasoningModel(Boolean reasoningModel) {
        this.reasoningModel = reasoningModel;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getAuthHeader() {
        return authHeader;
    }

    public void setAuthHeader(String authHeader) {
        this.authHeader = authHeader;
    }

    public boolean usesApiKeyHeader() {
        return "api-key".equalsIgnoreCase(authHeader);
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
