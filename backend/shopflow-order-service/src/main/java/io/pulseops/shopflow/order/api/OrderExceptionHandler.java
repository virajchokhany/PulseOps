package io.pulseops.shopflow.order.api;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.pulseops.domain.CorrelationId;
import io.pulseops.shopflow.order.client.PaymentUnavailableException;

/**
 * A failed payment is reported as 502, not 400.
 *
 * <p>The customer's request was valid; a downstream dependency failed. Reporting it as a client
 * error would keep order-service looking healthy while customers cannot buy anything.
 */
@RestControllerAdvice
public class OrderExceptionHandler {

    public record ErrorResponse(String error, String message, String correlationId, Instant timestamp) {
    }

    @ExceptionHandler(PaymentUnavailableException.class)
    public ResponseEntity<ErrorResponse> handlePaymentUnavailable(PaymentUnavailableException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("PAYMENT_UNAVAILABLE", e.getMessage(), CorrelationId.current(), Instant.now()));
    }
}
