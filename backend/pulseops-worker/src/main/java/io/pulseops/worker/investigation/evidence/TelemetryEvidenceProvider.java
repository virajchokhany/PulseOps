package io.pulseops.worker.investigation.evidence;

import java.util.List;

import org.springframework.stereotype.Component;

import io.pulseops.domain.evidence.ServiceTelemetrySummary;
import io.pulseops.infrastructure.telemetry.TelemetrySummarizer;

/**
 * Supplies the incident window''s telemetry to the evidence package. The aggregation itself lives in
 * the infrastructure layer so the API can show an operator exactly the figures the model was given.
 */
@Component
public class TelemetryEvidenceProvider implements EvidenceProvider<List<ServiceTelemetrySummary>> {

    private final TelemetrySummarizer summarizer;

    public TelemetryEvidenceProvider(TelemetrySummarizer summarizer) {
        this.summarizer = summarizer;
    }

    @Override
    public String name() {
        return "telemetry";
    }

    @Override
    public List<ServiceTelemetrySummary> collect(EvidenceScope scope) {
        return summarizer.summarise(scope.affectedServices(), scope.windowFrom(), scope.windowTo());
    }
}
