package io.pulseops.api.incident;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.pulseops.domain.evidence.ServiceTelemetrySummary;
import io.pulseops.infrastructure.incident.IncidentEntity;
import io.pulseops.infrastructure.incident.IncidentRepository;
import io.pulseops.infrastructure.telemetry.TelemetrySummarizer;

/**
 * Replays the telemetry behind an incident for a reader.
 *
 * <p>It deliberately reuses the aggregation the investigation itself runs, over the same window, so
 * the figures on screen are the figures the analysis was reasoning about. Recomputing them a second
 * way would let the page and the RCA disagree without either being obviously wrong.
 */
@RestController
@RequestMapping("/api/incidents")
public class IncidentTelemetryController {

    private final IncidentRepository incidentRepository;
    private final TelemetrySummarizer summarizer;
    private final Duration windowBefore;

    public IncidentTelemetryController(IncidentRepository incidentRepository,
                                       TelemetrySummarizer summarizer,
                                       @Value("${pulseops.api.telemetry-window-before:15m}") Duration windowBefore) {
        this.incidentRepository = incidentRepository;
        this.summarizer = summarizer;
        this.windowBefore = windowBefore;
    }

    @GetMapping("/{incidentKey}/telemetry")
    @Transactional(readOnly = true)
    public List<ServiceTelemetrySummary> telemetry(@PathVariable UUID incidentKey) {
        IncidentEntity incident = incidentRepository.findById(incidentKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown incident"));

        return summarizer.summarise(
                incident.getAffectedServices(),
                incident.getOpenedAt().minus(windowBefore),
                Instant.now());
    }
}
