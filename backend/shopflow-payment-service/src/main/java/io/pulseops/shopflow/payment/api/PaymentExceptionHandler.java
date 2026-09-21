package io.pulseops.shopflow.payment.api;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.pulseops.domain.CorrelationId;
import io.pulseops.shopflow.payment.client.PaymentProviderException;
import io.pulseops.shopflow.payment.client.PaymentProviderTimeoutException;

/**
 * Maps provider failures to 5xx.
 *
 * <p>These are deliberately server-side status codes: the caller did nothing wrong, and the Alert
 * Engine counts 5xx to decide that a service is unhealthy. Returning 4xx here would hide a real
 * outage from the platform.
 */
@RestControllerAdvice
public class PaymentExceptionHandler {

    public record ErrorResponse(String error, String message, String correlationId, Instant timestamp) {

        static ErrorResponse of(String error, String message) {
            return new ErrorResponse(error, message, CorrelationId.current(), Instant.now());
        }
    }

    @ExceptionHandler(PaymentProviderTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleTimeout(PaymentProviderTimeoutException e) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(ErrorResponse.of("PAYMENT_PROVIDER_TIMEOUT", e.getMessage()));
    }

    @ExceptionHandler(PaymentProviderException.class)
    public ResponseEntity<ErrorResponse> handleProviderFailure(PaymentProviderException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of("PAYMENT_PROVIDER_UNAVAILABLE", e.getMessage()));
    }
}
