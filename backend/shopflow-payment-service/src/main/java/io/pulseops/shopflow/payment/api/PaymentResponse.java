package io.pulseops.shopflow.payment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.pulseops.shopflow.payment.domain.Payment;
import io.pulseops.shopflow.payment.domain.PaymentStatus;

public record PaymentResponse(
        UUID paymentId,
        UUID orderId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String providerReference,
        String failureReason,
        Instant createdAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getProviderReference(),
                payment.getFailureReason(),
                payment.getCreatedAt());
    }
}
