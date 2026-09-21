package io.pulseops.infrastructure.telemetry;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TelemetryEventRepository extends JpaRepository<TelemetryEventEntity, Long> {

    List<TelemetryEventEntity> findByServiceAndOccurredAtBetweenOrderByOccurredAtDesc(
            String service, Instant from, Instant to);

    /** Bounded variant: evidence collection must not load an unbounded window into memory. */
    List<TelemetryEventEntity> findByServiceAndOccurredAtBetweenOrderByOccurredAtDesc(
            String service, Instant from, Instant to, Limit limit);

    @Query("""
            SELECT t FROM TelemetryEventEntity t
            WHERE t.service = :service
              AND t.occurredAt >= :from
              AND t.statusCode >= 500
            ORDER BY t.occurredAt DESC
            """)
    List<TelemetryEventEntity> findRecentErrors(@Param("service") String service, @Param("from") Instant from);
}
