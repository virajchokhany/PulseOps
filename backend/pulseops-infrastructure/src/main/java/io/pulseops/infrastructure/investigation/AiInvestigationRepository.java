package io.pulseops.infrastructure.investigation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiInvestigationRepository extends JpaRepository<AiInvestigationEntity, Long> {

    List<AiInvestigationEntity> findByIncidentKeyOrderByStartedAtDesc(UUID incidentKey);

    Optional<AiInvestigationEntity> findFirstByIncidentKeyAndStatusOrderByStartedAtDesc(
            UUID incidentKey, String status);
}
