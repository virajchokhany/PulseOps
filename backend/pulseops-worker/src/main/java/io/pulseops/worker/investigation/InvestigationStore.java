package io.pulseops.worker.investigation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.pulseops.domain.RcaStatus;
import io.pulseops.domain.evidence.InvestigationContext;
import io.pulseops.domain.rca.RootCauseAnalysis;
import io.pulseops.infrastructure.incident.IncidentEntity;
import io.pulseops.infrastructure.incident.IncidentRepository;
import io.pulseops.infrastructure.incident.IncidentTimelineEntity;
import io.pulseops.infrastructure.incident.IncidentTimelineRepository;
import io.pulseops.infrastructure.investigation.AiInvestigationEntity;
import io.pulseops.infrastructure.investigation.AiInvestigationRepository;

/**
 * Transactional boundary for investigation state.
 *
 * <p>Separated from the worker because the worker runs its steps on a scheduler thread, and each
 * step needs its own short transaction rather than one long transaction spanning an external call.
 * Holding a database connection open across an LLM round trip would tie up the pool for the
 * duration of the slowest thing in the system.
 */
@Component
public class InvestigationStore {

    private static final Logger log = LoggerFactory.getLogger(InvestigationStore.class);

    private final IncidentRepository incidentRepository;
    private final AiInvestigationRepository investigationRepository;
    private final IncidentTimelineRepository timelineRepository;
    private final ObjectMapper objectMapper;

    public InvestigationStore(IncidentRepository incidentRepository,
                              AiInvestigationRepository investigationRepository,
                              IncidentTimelineRepository timelineRepository,
                              ObjectMapper objectMapper) {
        this.incidentRepository = incidentRepository;
        this.investigationRepository = investigationRepository;
        this.timelineRepository = timelineRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Optional<IncidentEntity> loadIncident(UUID incidentKey) {
        return incidentRepository.findById(incidentKey);
    }

    /** Invalidates a completed RCA without deleting it, so the operator keeps reading the old one. */
    @Transactional
    public void markStale(UUID incidentKey) {
        incidentRepository.findById(incidentKey).ifPresent(IncidentEntity::markRcaStale);
    }

    @Transactional
    public AiInvestigationEntity begin(UUID incidentKey, long incidentVersion, String correlationId) {
        AiInvestigationEntity investigation =
                new AiInvestigationEntity(incidentKey, incidentVersion, "IN_PROGRESS", 1, correlationId);
        investigationRepository.save(investigation);
        incidentRepository.findById(incidentKey)
                .ifPresent(incident -> incident.setRcaStatus(RcaStatus.IN_PROGRESS));
        return investigation;
    }

    @Transactional
    public void complete(Long investigationId, UUID incidentKey, long incidentVersionAtStart,
                         RootCauseAnalysis analysis, InvestigationContext context,
                         String provider, String model, long durationMs) {
        AiInvestigationEntity investigation = investigationRepository.findById(investigationId).orElseThrow();
        investigation.succeeded(provider, model, toJson(analysis), toJson(summarise(context)), durationMs, 0);

        incidentRepository.findById(incidentKey).ifPresent(incident -> {
            incident.setCurrentRcaId(investigation.getId());
            // The incident may have moved on while the model was thinking. The RCA is still worth
            // keeping, but it describes an older version, so it is born stale rather than current.
            boolean supersededWhileRunning = incident.getVersion() > incidentVersionAtStart;
            incident.setRcaStatus(supersededWhileRunning ? RcaStatus.STALE : RcaStatus.COMPLETED);
            if (supersededWhileRunning) {
                log.info("Incident {} advanced from v{} to v{} during the investigation; RCA marked STALE",
                        incidentKey, incidentVersionAtStart, incident.getVersion());
            }
        });

        timelineRepository.save(new IncidentTimelineEntity(
                incidentKey, Instant.now(), "RCA_COMPLETED",
                "Automated analysis completed (confidence %.2f) via %s"
                        .formatted(analysis.confidence() == null ? 0 : analysis.confidence(), provider),
                null));
    }

    @Transactional
    public void fail(Long investigationId, UUID incidentKey, String reason, long durationMs) {
        investigationRepository.findById(investigationId)
                .ifPresent(investigation -> investigation.failed(reason, durationMs));
        incidentRepository.findById(incidentKey)
                .ifPresent(incident -> incident.setRcaStatus(RcaStatus.FAILED));
        timelineRepository.save(new IncidentTimelineEntity(
                incidentKey, Instant.now(), "RCA_FAILED", "Automated analysis failed: " + reason, null));
    }

    /**
     * Records a failure for an incident that never became visible to this worker. There is no
     * incident row to update, so the investigation row is the only durable record that the platform
     * tried and gave up.
     */
    @Transactional
    public void recordUnavailableIncident(UUID incidentKey, long incidentVersion,
                                          String reason, String correlationId) {
        AiInvestigationEntity investigation =
                new AiInvestigationEntity(incidentKey, incidentVersion, "FAILED", 1, correlationId);
        investigation.failed(reason, 0);
        investigationRepository.save(investigation);
    }

    private EvidenceSummary summarise(InvestigationContext context) {
        return new EvidenceSummary(
                context.alerts().size(), context.telemetry().size(), context.deployments().size(),
                context.pullRequests().size(), context.codeSnippets().size(),
                context.runbooks().size(), context.missingEvidence());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialise investigation output", e);
        }
    }

    /** Stored alongside the RCA so a weak analysis can be attributed to thin evidence. */
    private record EvidenceSummary(
            int alerts, int telemetryGroups, int deployments, int pullRequests,
            int codeSnippets, int runbooks, java.util.List<String> missingEvidence) {
    }
}
