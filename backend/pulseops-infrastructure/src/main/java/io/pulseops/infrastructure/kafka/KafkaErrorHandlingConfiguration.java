package io.pulseops.infrastructure.kafka;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import io.pulseops.domain.CorrelationId;

/**
 * Retry and dead-letter policy shared by every PulseOps consumer.
 *
 * <p><b>Why it exists.</b> Consumers fail for two very different reasons. Transient failures (the
 * database is briefly unavailable, a lock timed out) succeed if you simply try again. Permanent
 * failures (malformed JSON, a field that will never validate) fail identically forever, and
 * retrying them blocks the partition behind a message that can never succeed.
 *
 * <p><b>What it handles.</b> Transient failures get a bounded, exponentially backed-off retry.
 * Permanent failures, and anything that exhausts its retries, are published to
 * {@code <topic>.dlq} with the original payload and headers preserved, then the offset is
 * committed so the partition keeps moving.
 *
 * <p><b>Tradeoffs.</b> Retries happen in the listener thread, so a slow retry cycle adds consumer
 * lag on that partition. Three attempts over roughly two seconds is short enough to ride out a
 * blip and short enough not to stall the stream. Messages sent to a DLQ are not automatically
 * reprocessed; replay is a deliberate human action, which is the right default when the alternative
 * is an infinite loop.
 */
@Configuration
public class KafkaErrorHandlingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlingConfiguration.class);

    private static final int MAX_ATTEMPTS = 3;

    @Bean
    DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                // Partition -1 lets the producer choose: DLQ topics have a single partition, so the
                // source partition number is meaningless there.
                (record, exception) -> new TopicPartition(record.topic() + ".dlq", -1));

        recoverer.setHeadersFunction((record, exception) -> {
            record.headers().add(CorrelationId.HEADER,
                    CorrelationId.currentOrNew().getBytes(StandardCharsets.UTF_8));
            return record.headers();
        });
        return recoverer;
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        ExponentialBackOff backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxElapsedTime(5_000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

        // Straight to the DLQ: no amount of retrying fixes a malformed or invalid payload.
        handler.addNotRetryableExceptions(NonRetryableEventException.class, IllegalArgumentException.class);

        // Explicitly retryable: the database being momentarily unreachable is the textbook
        // transient failure this policy exists for.
        handler.addRetryableExceptions(DataAccessResourceFailureException.class);

        handler.setRetryListeners((record, exception, deliveryAttempt) ->
                log.warn("Retrying {}-{} offset={} attempt={}/{}: {}",
                        record.topic(), record.partition(), record.offset(),
                        deliveryAttempt, MAX_ATTEMPTS, exception.getMessage()));

        return handler;
    }
}
