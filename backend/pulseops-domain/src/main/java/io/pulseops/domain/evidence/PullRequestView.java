package io.pulseops.domain.evidence;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PullRequestView(
        int number,
        String repository,
        String title,
        String description,
        String author,
        String commitSha,
        Instant mergedAt,
        String url,
        List<String> changedFiles) {
}
