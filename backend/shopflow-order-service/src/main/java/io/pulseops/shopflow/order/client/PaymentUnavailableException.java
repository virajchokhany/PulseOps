package io.pulseops.shopflow.order.client;

/** Payment authorisation could not be completed. Carries the upstream status for telemetry. */
public class PaymentUnavailableException extends RuntimeException {

    private final int upstreamStatus;

    public PaymentUnavailableException(String message, int upstreamStatus) {
        super(message);
        this.upstreamStatus = upstreamStatus;
    }

    public int getUpstreamStatus() {
        return upstreamStatus;
    }
}
