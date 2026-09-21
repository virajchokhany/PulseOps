package io.pulseops.infrastructure.alert;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<AlertEntity, String> {

    List<AlertEntity> findByIncidentKeyOrderByOccurredAtAsc(UUID incidentKey);

    List<AlertEntity> findByServiceAndOccurredAtAfterOrderByOccurredAtDesc(String service, Instant after);

    List<AlertEntity> findAllByOrderByOccurredAtDesc(Limit limit);
}
