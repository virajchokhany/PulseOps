package io.pulseops.worker.investigation.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The reasoning flag decides which parameters go on the wire, and it is inferred from a string.
 * Getting it wrong produces a 400 that reads like a malformed request rather than a bad option,
 * so the classification is pinned here.
 */
class LlmPropertiesTest {

    private LlmProperties propertiesFor(String model) {
        LlmProperties properties = new LlmProperties();
        properties.setModel(model);
        return properties;
    }

    @Test
    @DisplayName("gpt-4.1-mini is not a reasoning model despite the newer version number")
    void gpt41MiniIsNotAReasoningModel() {
        assertThat(propertiesFor("gpt-4.1-mini").isReasoningModel()).isFalse();
    }

    @Test
    void gpt5AndOSeriesAreReasoningModels() {
        assertThat(propertiesFor("gpt-5").isReasoningModel()).isTrue();
        assertThat(propertiesFor("o3-mini").isReasoningModel()).isTrue();
        assertThat(propertiesFor("o4-mini").isReasoningModel()).isTrue();
    }

    @Test
    @DisplayName("a non-reasoning model gets the full budget, since none is spent on reasoning")
    void nonReasoningModelUsesTheVisibleOutputBudget() {
        LlmProperties properties = propertiesFor("gpt-4.1-mini");

        // A measured RCA emitted ~2,650 visible tokens, so this must comfortably exceed that.
        assertThat(properties.effectiveMaxOutputTokens()).isEqualTo(4000);
        assertThat(properties.effectiveMaxOutputTokens()).isGreaterThan(2650);
    }

    @Test
    void reasoningModelGetsTheLargerBudget() {
        assertThat(propertiesFor("gpt-5").effectiveMaxOutputTokens()).isEqualTo(8000);
    }

    @Test
    @DisplayName("an explicit override beats the name, for deployments named after another family")
    void explicitOverrideBeatsNameInference() {
        LlmProperties named = propertiesFor("gpt-5-cheap");
        named.setReasoningModel(false);

        assertThat(named.isReasoningModel()).isFalse();
        assertThat(named.effectiveMaxOutputTokens()).isEqualTo(4000);
    }

    @Test
    void modelNameIsClassifiedCaseInsensitively() {
        assertThat(propertiesFor("GPT-5").isReasoningModel()).isTrue();
    }
}
