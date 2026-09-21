package io.pulseops.worker.investigation.llm;

import io.pulseops.domain.evidence.InvestigationContext;
import io.pulseops.domain.rca.RootCauseAnalysis;

/**
 * Produces a root cause analysis from an evidence package.
 *
 * <p>An interface so the platform is not coupled to one vendor, and so the pipeline can be run and
 * tested end to end without a network call or an API key.
 */
public interface LlmProvider {

    String name();

    String model();

    /**
     * @throws LlmException when the analysis could not be produced or did not validate
     */
    RootCauseAnalysis analyse(InvestigationContext context);
}
