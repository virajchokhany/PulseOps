package io.pulseops.shopflow.payment.client;

/** The provider answered, but refused the payment for an operational reason. */
public class PaymentProviderException extends RuntimeException {

    public PaymentProviderException(String message) {
        super(message);
    }
}
