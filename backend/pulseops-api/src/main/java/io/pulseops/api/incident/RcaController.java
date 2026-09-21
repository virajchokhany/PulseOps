package io.pulseops.api.incident;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.pulseops.domain.RcaStatus;
import io.pulseops.infrastructure.incident.IncidentEntity;
import io.pulseops.infrastructure.incident.IncidentRepository;
import io.pulseops.infrastructure.investigation.AiInvestigationEntity;
import io.pulseops.infrastructure.investigation.AiInvestigationRepository;

/**
 * Exposes the current automated analysis.
 *
 * <p>Read-only and poll-friendly: the dashboard re-fetches this and renders whatever the latest
 * state is. There is no endpoint to trigger an investigation, because investigations are triggered
 * by incident events rather than by a user.
 */
@RestController
@RequestMapping("/api/incidents")
public class RcaController {

    public record InvestigationAttempt(
            Long id, String status, String provider, String model,
            Instant startedAt, Instant completedAt, Long durationMs, String failureReason) {
    }

    public record RcaResponse(
            UUID incidentKey,
            RcaStatus rcaStatus,
            JsonNode rca,
            JsonNode evidenceSummary,
            List<InvestigationAttempt> history) {
    }

    private final IncidentRepository incidentRepository;
    private final AiInvestigationRepository investigationRepository;
    private final ObjectMapper objectMapper;

    public RcaController(IncidentRepository incidentRepository,
                         AiInvestigationRepository investigationRepository,
                         ObjectMapper objectMapper) {
        this.incidentRepository = incidentRepository;
        this.investigationRepository = investigationRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{incidentKey}/rca")
    @Transactional(readOnly = true)
    public RcaResponse rca(@PathVariable UUID incidentKey) {
        IncidentEntity incident = incidentRepository.findById(incidentKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown incident"));

        List<AiInvestigationEntity> attempts =
                investigationRepository.findByIncidentKeyOrderByStartedAtDesc(incidentKey);

        AiInvestigationEntity current = attempts.stream()
                .filter(attempt -> attempt.getId().equals(incident.getCurrentRcaId()))
                .findFirst()
                // A STALE analysis is still the best available answer, so fall back to the most
                // recent completed attempt rather than showing nothing.
                .or(() -> attempts.stream()
                        .filter(attempt -> "COMPLETED".equals(attempt.getStatus()))
                        .findFirst())
                .orElse(null);

        return new RcaResponse(
                incidentKey,
                incident.getRcaStatus(),
                current == null ? null : readJson(current.getRca()),
                current == null ? null : readJson(current.getEvidenceSummary()),
                attempts.stream().map(RcaController::toAttempt).toList());
    }

    private static InvestigationAttempt toAttempt(AiInvestigationEntity entity) {
        return new InvestigationAttempt(
                entity.getId(), entity.getStatus(), entity.getProvider(), entity.getModel(),
                entity.getStartedAt(), entity.getCompletedAt(), entity.getDurationMs(),
                entity.getFailureReason());
    }

    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored RCA is not valid JSON", e);
        }
    }
}
