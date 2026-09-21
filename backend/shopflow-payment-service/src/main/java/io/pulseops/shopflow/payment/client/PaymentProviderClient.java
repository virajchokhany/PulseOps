package io.pulseops.shopflow.payment.client;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import io.pulseops.shopflow.common.telemetry.TelemetryPublisher;
import io.pulseops.shopflow.payment.failure.FailureInjector;

/**
 * Talks to the external payment provider.
 *
 * <p>The provider itself is simulated, but the failure modes are the real ones: it can be slow, and
 * it can refuse. Every outbound attempt emits DEPENDENCY telemetry so PulseOps can distinguish
 * "payment-service is broken" from "payment-service's provider is broken".
 *
 * <p>Note the interaction between {@code readTimeout} and provider latency. With the original 800ms
 * budget a slow provider produced fast failures. Since PR #482 raised the timeout to 5s, a provider
 * that answers in ~2s produces slow <em>successes</em> instead, so latency climbs across the board
 * and request threads stay occupied five times longer.
 */
@Component
@EnableConfigurationProperties(PaymentProviderProperties.class)
public class PaymentProviderClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentProviderClient.class);

    public record ProviderAuthorization(String reference, long latencyMs) {
    }

    private final PaymentProviderProperties properties;
    private final FailureInjector failureInjector;
    private final TelemetryPublisher telemetry;

    public PaymentProviderClient(PaymentProviderProperties properties,
                                 FailureInjector failureInjector,
                                 TelemetryPublisher telemetry) {
        this.properties = properties;
        this.failureInjector = failureInjector;
        this.telemetry = telemetry;
    }

    public ProviderAuthorization authorize(UUID orderId, BigDecimal amount, String currency) {
        long timeoutMs = properties.getReadTimeout().toMillis();
        long providerLatencyMs = properties.getBaseLatencyMs() + failureInjector.extraLatencyMs();
        long startNanos = System.nanoTime();

        if (providerLatencyMs >= timeoutMs) {
            sleep(timeoutMs);
            long waited = elapsedMs(startNanos);
            telemetry.emitDependency("payment-provider/authorize", 504, waited,
                    "Payment provider timeout after " + waited + "ms");
            throw new PaymentProviderTimeoutException(
                    "Payment provider did not respond within " + timeoutMs + "ms", waited);
        }

        sleep(providerLatencyMs);
        long latency = elapsedMs(startNanos);

        if (failureInjector.shouldFail()) {
            telemetry.emitDependency("payment-provider/authorize", 503, latency,
                    "Payment provider unavailable");
            throw new PaymentProviderException("Payment provider returned 503 Service Unavailable");
        }

        String reference = "PROV-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        telemetry.emitDependency("payment-provider/authorize", 200, latency, null);
        log.debug("Provider authorised order={} amount={} {} reference={} latencyMs={}",
                orderId, amount, currency, reference, latency);
        return new ProviderAuthorization(reference, latency);
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentProviderException("Interrupted while waiting for payment provider");
        }
    }
}
