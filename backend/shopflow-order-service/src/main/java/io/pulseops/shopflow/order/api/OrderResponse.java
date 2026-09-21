package io.pulseops.shopflow.order.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.pulseops.shopflow.order.domain.Order;
import io.pulseops.shopflow.order.domain.OrderStatus;

public record OrderResponse(
        UUID orderId,
        String customerId,
        BigDecimal amount,
        String currency,
        OrderStatus status,
        UUID paymentId,
        String failureReason,
        Instant createdAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getAmount(),
                order.getCurrency(),
                order.getStatus(),
                order.getPaymentId(),
                order.getFailureReason(),
                order.getCreatedAt());
    }
}
