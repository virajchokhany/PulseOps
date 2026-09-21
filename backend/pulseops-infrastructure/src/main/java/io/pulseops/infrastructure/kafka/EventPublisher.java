package io.pulseops.infrastructure.kafka;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import io.pulseops.domain.CorrelationId;

/** Publishes domain events, stamping every record with the current correlation id. */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final EventPayloadCodec codec;

    public EventPublisher(KafkaTemplate<String, String> kafkaTemplate, EventPayloadCodec codec) {
        this.kafkaTemplate = kafkaTemplate;
        this.codec = codec;
    }

    /**
     * @param key partition key. Keying by service name keeps each service's events in order on a
     *            single partition, which is what the sliding-window rules in the Alert Engine
     *            assume. Global ordering across services is neither needed nor worth one partition.
     */
    public CompletableFuture<SendResult<String, String>> publish(String topic, String key, Object event) {
        String correlationId = CorrelationId.currentOrNew();
        ProducerRecord<String, String> record =
                new ProducerRecord<>(topic, key, codec.encode(event));
        record.headers().add(CorrelationId.HEADER, correlationId.getBytes(StandardCharsets.UTF_8));

        return kafkaTemplate.send(record).whenComplete((result, error) -> {
            if (error != null) {
                log.error("Failed to publish to {} key={}: {}", topic, key, error.getMessage(), error);
            } else if (log.isDebugEnabled()) {
                log.debug("Published to {}-{} offset={} key={}", topic,
                        result.getRecordMetadata().partition(), result.getRecordMetadata().offset(), key);
            }
        });
    }
}
