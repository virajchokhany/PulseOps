package io.pulseops.infrastructure.deployment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentRepository extends JpaRepository<DeploymentEntity, Long> {

    /**
     * Deployments to the affected services shortly before an incident. "What changed just before
     * this broke" is the single most useful question during an outage, so it is the primary query.
     */
    List<DeploymentEntity> findByServiceNameInAndDeployedAtAfterOrderByDeployedAtDesc(
            Collection<String> serviceNames, Instant after);

    Optional<DeploymentEntity> findFirstByServiceNameOrderByDeployedAtDesc(String serviceName);

    List<DeploymentEntity> findByServiceNameOrderByDeployedAtDesc(String serviceName);
}
