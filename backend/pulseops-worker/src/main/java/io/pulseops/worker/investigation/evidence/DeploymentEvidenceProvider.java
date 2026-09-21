package io.pulseops.worker.investigation.evidence;

import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.pulseops.domain.evidence.DeploymentView;
import io.pulseops.infrastructure.deployment.DeploymentRepository;

/**
 * Finds deployments to the affected services shortly before the incident.
 *
 * <p>"What shipped just before this broke" is the highest-yield question in an outage, so the
 * lookback deliberately extends further back than the telemetry window.
 */
@Component
public class DeploymentEvidenceProvider implements EvidenceProvider<List<DeploymentView>> {

    private static final Duration LOOKBACK = Duration.ofHours(24);

    private final DeploymentRepository repository;

    public DeploymentEvidenceProvider(DeploymentRepository repository) {
        this.repository = repository;
    }

    @Override
    public String name() {
        return "deployments";
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeploymentView> collect(EvidenceScope scope) {
        return repository
                .findByServiceNameInAndDeployedAtAfterOrderByDeployedAtDesc(
                        scope.affectedServices(), scope.incidentOpenedAt().minus(LOOKBACK))
                .stream()
                .map(deployment -> new DeploymentView(
                        deployment.getServiceName(),
                        deployment.getVersion(),
                        deployment.getCommitSha(),
                        deployment.getEnvironment(),
                        deployment.getDeployedBy(),
                        deployment.getDeployedAt(),
                        deployment.getStatus(),
                        deployment.getNotes(),
                        Duration.between(deployment.getDeployedAt(), scope.incidentOpenedAt()).toMinutes()))
                .toList();
    }
}
