package io.pulseops.infrastructure.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guards Kafka consumers against duplicate delivery.
 *
 * <p>Kafka is at-least-once: a rebalance, a retry or a replayed offset can hand the same event to a
 * consumer twice. Rather than claiming exactly-once semantics, each consumer asks this guard whether
 * it is the first to see an event id and skips the work if it is not.
 */
@Component
public class IdempotencyGuard {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyGuard.class);

    private final ProcessedEventRepository repository;

    public IdempotencyGuard(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Must run inside the caller's transaction, hence {@link Propagation#MANDATORY}.
     *
     * <p>If the claim committed separately from the work it guards, a transient failure after the
     * claim would leave the event marked as processed while nothing was written, and the retry
     * would skip it. Sharing one transaction means a rollback releases the claim and the retry
     * genuinely reprocesses the event.
     *
     * <p>Concurrency is handled by the database: two consumers racing on the same event id both
     * issue {@code ON CONFLICT DO NOTHING}; the second blocks until the first commits and then sees
     * zero rows affected.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean claim(String consumer, String eventId) {
        boolean claimed = repository.claim(consumer, eventId) > 0;
        if (!claimed) {
            log.debug("Skipping duplicate event consumer={} eventId={}", consumer, eventId);
        }
        return claimed;
    }
}
