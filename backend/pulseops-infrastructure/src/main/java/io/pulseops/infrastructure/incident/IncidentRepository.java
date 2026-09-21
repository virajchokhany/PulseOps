package io.pulseops.infrastructure.incident;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import io.pulseops.domain.IncidentStatus;

public interface IncidentRepository extends JpaRepository<IncidentEntity, UUID> {

    List<IncidentEntity> findByStatusOrderByOpenedAtDesc(IncidentStatus status);

    List<IncidentEntity> findAllByOrderByOpenedAtDesc(Limit limit);
}
