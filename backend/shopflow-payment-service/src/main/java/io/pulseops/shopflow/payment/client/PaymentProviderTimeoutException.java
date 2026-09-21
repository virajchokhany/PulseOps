package io.pulseops.shopflow.payment.client;

/** The provider did not answer within the configured read timeout. */
public class PaymentProviderTimeoutException extends RuntimeException {

    private final long waitedMs;

    public PaymentProviderTimeoutException(String message, long waitedMs) {
        super(message);
        this.waitedMs = waitedMs;
    }

    public long getWaitedMs() {
        return waitedMs;
    }
}
