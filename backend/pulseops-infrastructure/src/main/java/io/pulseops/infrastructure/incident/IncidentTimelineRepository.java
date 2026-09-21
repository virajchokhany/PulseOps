package io.pulseops.infrastructure.incident;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentTimelineRepository extends JpaRepository<IncidentTimelineEntity, Long> {

    List<IncidentTimelineEntity> findByIncidentKeyOrderByOccurredAtAsc(UUID incidentKey);
}
