package io.pulseops.infrastructure.kafka;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.pulseops.domain.CorrelationId;

/**
 * Converts events to and from JSON strings on the wire.
 *
 * <p>Topics carry plain {@code String} values rather than using Spring's {@code JsonDeserializer}.
 * That is deliberate: with a typed deserializer, malformed JSON fails inside the Kafka consumer
 * before any listener runs, which makes a poison message hard to route and easy to loop on. Keeping
 * the payload as a string means deserialization failures are ordinary application errors that the
 * listener's error handler can send to the DLQ with the original bytes intact.
 */
@Component
public class EventPayloadCodec {

    private final ObjectMapper objectMapper;

    public EventPayloadCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialise event of type " + event.getClass().getName(), e);
        }
    }

    public <T> T decode(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JsonProcessingException e) {
            throw new NonRetryableEventException(
                    "Malformed " + type.getSimpleName() + " payload: " + e.getOriginalMessage(), e);
        }
    }

    /** Adopts the correlation id carried on the record so consumer logs line up with producer logs. */
    public static String adoptCorrelationId(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(CorrelationId.HEADER);
        String correlationId = header != null
                ? new String(header.value(), StandardCharsets.UTF_8)
                : CorrelationId.currentOrNew();
        CorrelationId.set(correlationId);
        return correlationId;
    }
}
