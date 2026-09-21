package io.pulseops.shopflow.order.client;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shopflow.order.payment")
public class PaymentClientProperties {

    private String baseUrl = "http://localhost:8083";

    private Duration connectTimeout = Duration.ofSeconds(2);

    /**
     * Must be longer than payment-service's provider read timeout. If order-service gave up first,
     * every payment problem would surface as an order-service timeout and the real cause would be
     * one service further away than it looks.
     */
    private Duration readTimeout = Duration.ofSeconds(8);

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
