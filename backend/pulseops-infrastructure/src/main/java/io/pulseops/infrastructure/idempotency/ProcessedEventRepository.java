package io.pulseops.infrastructure.idempotency;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEvent.Key> {

    /**
     * Atomically claims an event for a consumer.
     *
     * <p>Returns 1 when this caller won the claim and 0 when the event was already processed.
     * Done as a single {@code ON CONFLICT DO NOTHING} insert rather than a read-then-write so that
     * two consumer instances handling the same redelivered message cannot both decide they are first.
     */
    @Modifying
    @Query(value = """
            INSERT INTO processed_events (consumer, event_id, processed_at)
            VALUES (:consumer, :eventId, now())
            ON CONFLICT (consumer, event_id) DO NOTHING
            """, nativeQuery = true)
    int claim(@Param("consumer") String consumer, @Param("eventId") String eventId);

    @Modifying
    @Query(value = "DELETE FROM processed_events WHERE processed_at < :before", nativeQuery = true)
    int deleteProcessedBefore(@Param("before") Instant before);
}
