package io.pulseops.worker.investigation.evidence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** What an evidence provider is allowed to know about the incident it is gathering evidence for. */
public record EvidenceScope(
        UUID incidentKey,
        String primaryService,
        List<String> affectedServices,
        Instant incidentOpenedAt,
        Instant windowFrom,
        Instant windowTo,
        List<String> alertTypes) {
}
