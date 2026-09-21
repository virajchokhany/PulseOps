package io.pulseops.worker.investigation.evidence;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.pulseops.domain.evidence.CodeSnippet;
import io.pulseops.domain.evidence.PullRequestView;
import io.pulseops.infrastructure.catalog.ServiceEntity;
import io.pulseops.infrastructure.catalog.ServiceRepository;
import io.pulseops.infrastructure.sourcecontrol.SourceControlProvider;

/**
 * Retrieves the small pieces of source code that a suspect pull request actually touched.
 *
 * <p>Selective by construction: only files listed on a recent pull request are considered, and only
 * a window around a relevant keyword is read. Sending whole repositories would bury the signal and
 * invite the model to speculate about unrelated code.
 */
@Component
public class SourceCodeEvidenceProvider implements EvidenceProvider<List<CodeSnippet>> {

    /** Terms that tend to matter for the failure modes PulseOps alerts on. */
    private static final List<String> LATENCY_HINTS = List.of("timeout", "latency", "duration", "wait");
    private static final List<String> ERROR_HINTS = List.of("retry", "timeout", "exception", "error", "circuit");
    private static final Set<String> STOPWORDS =
            Set.of("increase", "update", "change", "improve", "remove", "the", "and", "for", "with", "from");

    private final PullRequestEvidenceProvider pullRequestProvider;
    private final ServiceRepository serviceRepository;
    private final SourceControlProvider sourceControlProvider;

    public SourceCodeEvidenceProvider(PullRequestEvidenceProvider pullRequestProvider,
                                      ServiceRepository serviceRepository,
                                      SourceControlProvider sourceControlProvider) {
        this.pullRequestProvider = pullRequestProvider;
        this.serviceRepository = serviceRepository;
        this.sourceControlProvider = sourceControlProvider;
    }

    @Override
    public String name() {
        return "codeSnippets";
    }

    @Override
    @Transactional(readOnly = true)
    public List<CodeSnippet> collect(EvidenceScope scope) {
        List<PullRequestView> pullRequests = pullRequestProvider.collect(scope);
        if (pullRequests.isEmpty()) {
            return List.of();
        }

        ServiceEntity primary = serviceRepository.findById(scope.primaryService()).orElse(null);
        String sourcePath = primary != null ? primary.getSourcePath() : null;
        String repository = primary != null ? primary.getRepository() : null;

        List<CodeSnippet> snippets = new ArrayList<>();
        for (PullRequestView pullRequest : pullRequests) {
            if (pullRequest.changedFiles().isEmpty()) {
                continue;
            }
            snippets.addAll(sourceControlProvider.fetchSnippets(new SourceControlProvider.SnippetRequest(
                    scope.primaryService(),
                    repository != null ? repository : pullRequest.repository(),
                    sourcePath,
                    pullRequest.commitSha(),
                    pullRequest.changedFiles(),
                    keywordsFor(scope, pullRequest),
                    "changed by PR #" + pullRequest.number() + " (" + pullRequest.title() + ")")));
        }
        return snippets;
    }

    /** Keywords come from the alert types that fired and from the pull request's own title. */
    private List<String> keywordsFor(EvidenceScope scope, PullRequestView pullRequest) {
        Set<String> keywords = new LinkedHashSet<>();

        for (String alertType : scope.alertTypes()) {
            if (alertType.contains("Latency")) {
                keywords.addAll(LATENCY_HINTS);
            }
            if (alertType.contains("Error") || alertType.contains("Failure")) {
                keywords.addAll(ERROR_HINTS);
            }
        }

        if (pullRequest.title() != null) {
            for (String word : pullRequest.title().toLowerCase(Locale.ROOT).split("\\W+")) {
                if (word.length() > 4 && !STOPWORDS.contains(word)) {
                    keywords.add(word);
                }
            }
        }
        return List.copyOf(keywords);
    }
}
