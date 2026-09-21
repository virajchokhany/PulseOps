package io.pulseops.infrastructure.sourcecontrol;

import java.util.List;

import io.pulseops.domain.evidence.CodeSnippet;

/**
 * Retrieves targeted source code for an investigation.
 *
 * <p>An interface rather than a concrete class so the MVP's local implementation can be replaced by
 * a GitHub or Azure Repos client without touching the AI worker. The worker asks for snippets; how
 * they are obtained is not its concern.
 */
public interface SourceControlProvider {

    /**
     * @param filePaths repository-relative paths, normally the files a suspect pull request touched
     * @param keywords  terms worth centring the snippet on, e.g. "timeout" for a latency incident
     */
    record SnippetRequest(
            String service,
            String repository,
            String sourcePath,
            String commitSha,
            List<String> filePaths,
            List<String> keywords,
            String reason) {
    }

    List<CodeSnippet> fetchSnippets(SnippetRequest request);
}
