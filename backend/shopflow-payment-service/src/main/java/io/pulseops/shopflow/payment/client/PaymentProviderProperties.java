package io.pulseops.shopflow.payment.client;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shopflow.payment.provider")
public class PaymentProviderProperties {

    /** Round-trip time to the provider when it is healthy. */
    private int baseLatencyMs = 120;

    /**
     * How long to wait for the provider before giving up.
     *
     * <p>Historically 800ms, which matched the documented provider budget and failed fast.
     * PR #482 raised it to 5s.
     */
    private Duration readTimeout = Duration.ofSeconds(5);

    public int getBaseLatencyMs() {
        return baseLatencyMs;
    }

    public void setBaseLatencyMs(int baseLatencyMs) {
        this.baseLatencyMs = baseLatencyMs;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
