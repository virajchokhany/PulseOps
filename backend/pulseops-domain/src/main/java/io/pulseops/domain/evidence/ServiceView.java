package io.pulseops.domain.evidence;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ServiceView(
        String name,
        String displayName,
        String owner,
        String repository,
        String environment,
        String version,
        String tier,
        String description,
        List<String> dependsOn,
        List<String> dependedOnBy) {
}
