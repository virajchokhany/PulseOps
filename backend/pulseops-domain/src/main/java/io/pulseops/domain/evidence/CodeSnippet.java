package io.pulseops.domain.evidence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A small, targeted extract of source code included in the evidence package.
 *
 * <p>Snippets are deliberately narrow. The LLM is given the few lines that a suspect pull request
 * touched, not a repository, because a large context dilutes the signal and invites the model to
 * speculate about code that has nothing to do with the incident.
 *
 * @param reason why this snippet was selected, so the model can weigh it rather than guess
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CodeSnippet(
        String service,
        String repository,
        String filePath,
        String commitSha,
        int startLine,
        int endLine,
        String content,
        String reason) {
}
