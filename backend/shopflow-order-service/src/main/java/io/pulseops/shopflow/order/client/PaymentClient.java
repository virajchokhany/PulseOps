package io.pulseops.shopflow.order.client;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import io.pulseops.domain.CorrelationId;
import io.pulseops.shopflow.common.telemetry.TelemetryPublisher;

/** Synchronous call to payment-service. This edge is what makes payment failures become order failures. */
@Component
public class PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentClient.class);

    public record PaymentRequest(UUID orderId, BigDecimal amount, String currency) {
    }

    public record PaymentResponse(UUID paymentId, UUID orderId, String status, String providerReference) {
    }

    private final RestClient restClient;
    private final TelemetryPublisher telemetry;

    public PaymentClient(@Qualifier("paymentRestClient") RestClient restClient, TelemetryPublisher telemetry) {
        this.restClient = restClient;
        this.telemetry = telemetry;
    }

    public PaymentResponse authorize(UUID orderId, BigDecimal amount, String currency) {
        long startNanos = System.nanoTime();
        try {
            PaymentResponse response = restClient.post()
                    .uri("/payments")
                    // Propagating the header is what makes one customer request traceable across
                    // both services, and later across alerts and the incident.
                    .header(CorrelationId.HEADER, CorrelationId.currentOrNew())
                    .body(new PaymentRequest(orderId, amount, currency))
                    .retrieve()
                    .body(PaymentResponse.class);

            telemetry.emitDependency("payment-service/payments", 201, elapsedMs(startNanos), null);
            return response;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            telemetry.emitDependency("payment-service/payments", status, elapsedMs(startNanos), e.getMessage());
            log.warn("payment-service rejected order={} status={}", orderId, status);
            throw new PaymentUnavailableException("payment-service returned " + status, status);
        } catch (ResourceAccessException e) {
            // Connect/read timeout or connection refused: no HTTP status was ever received.
            telemetry.emitDependency("payment-service/payments", 504, elapsedMs(startNanos), e.getMessage());
            log.error("payment-service unreachable for order={}: {}", orderId, e.getMessage());
            throw new PaymentUnavailableException("payment-service unreachable: " + e.getMessage(), 504);
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
