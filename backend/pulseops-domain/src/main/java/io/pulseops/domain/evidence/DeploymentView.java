package io.pulseops.domain.evidence;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Deployment metadata included as evidence. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeploymentView(
        String service,
        String version,
        String commitSha,
        String environment,
        String deployedBy,
        Instant deployedAt,
        String status,
        String notes,
        /** Minutes between this deployment and the incident opening; negative means after. */
        long minutesBeforeIncident) {
}
