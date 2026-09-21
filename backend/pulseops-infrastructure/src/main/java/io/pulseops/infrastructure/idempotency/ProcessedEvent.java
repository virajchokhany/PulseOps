package io.pulseops.infrastructure.idempotency;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Row in the idempotency ledger: "consumer group X has already applied event Y". */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @EmbeddedId
    private Key key;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();

    protected ProcessedEvent() {
    }

    public ProcessedEvent(String consumer, String eventId) {
        this.key = new Key(consumer, eventId);
    }

    public Key getKey() {
        return key;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "consumer", nullable = false, length = 100)
        private String consumer;

        @Column(name = "event_id", nullable = false, length = 100)
        private String eventId;

        protected Key() {
        }

        public Key(String consumer, String eventId) {
            this.consumer = consumer;
            this.eventId = eventId;
        }

        public String getConsumer() {
            return consumer;
        }

        public String getEventId() {
            return eventId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key that)) {
                return false;
            }
            return Objects.equals(consumer, that.consumer) && Objects.equals(eventId, that.eventId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(consumer, eventId);
        }
    }
}
