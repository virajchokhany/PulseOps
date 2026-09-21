package io.pulseops.infrastructure.kafka;

/**
 * Marks a failure that retrying cannot fix: malformed JSON, a missing required field, a value that
 * will never validate. These go straight to the DLQ instead of burning the retry budget.
 */
public class NonRetryableEventException extends RuntimeException {

    public NonRetryableEventException(String message) {
        super(message);
    }

    public NonRetryableEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
