package io.pulseops.worker.investigation.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Selects the analysis provider at startup.
 *
 * <p>Presence of an API key is the only switch. With no key the deterministic provider runs and
 * nothing leaves the machine; adding {@code LLM_API_KEY} to the environment moves the same pipeline
 * onto a real model with no code change.
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LlmConfiguration.class);

    @Bean
    @Primary
    LlmProvider llmProvider(LlmProperties properties,
                            ObjectMapper objectMapper,
                            RuleBasedLlmProvider ruleBasedProvider) {
        if (!properties.hasApiKey()) {
            log.info("No LLM_API_KEY configured; using the deterministic rule-based provider");
            return ruleBasedProvider;
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.getTimeout().toMillis());
        factory.setReadTimeout((int) properties.getTimeout().toMillis());

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory);

        if (properties.usesApiKeyHeader()) {
            builder.defaultHeader("api-key", properties.getApiKey());
        } else {
            builder.defaultHeader("Authorization", "Bearer " + properties.getApiKey());
        }
        RestClient restClient = builder.build();

        log.info("Using OpenAI-compatible provider at {} with model {} (auth via {} header)",
                properties.getBaseUrl(), properties.getModel(),
                properties.usesApiKeyHeader() ? "api-key" : "Authorization");
        return new OpenAiLlmProvider(properties, restClient, objectMapper);
    }
}
