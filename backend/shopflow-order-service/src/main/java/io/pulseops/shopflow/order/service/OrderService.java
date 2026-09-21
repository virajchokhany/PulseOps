package io.pulseops.shopflow.order.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import io.pulseops.domain.CorrelationId;
import io.pulseops.shopflow.order.api.CreateOrderRequest;
import io.pulseops.shopflow.order.client.PaymentClient;
import io.pulseops.shopflow.order.client.PaymentUnavailableException;
import io.pulseops.shopflow.order.domain.Order;
import io.pulseops.shopflow.order.domain.OrderRepository;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository repository;
    private final PaymentClient paymentClient;

    public OrderService(OrderRepository repository, PaymentClient paymentClient) {
        this.repository = repository;
        this.paymentClient = paymentClient;
    }

    public Order place(CreateOrderRequest request) {
        Order order = new Order(
                request.customerId(), request.amount(), request.currencyOrDefault(), CorrelationId.current());
        // Persisted as PENDING before the payment call so a crash mid-authorisation leaves a record.
        repository.save(order);

        try {
            PaymentClient.PaymentResponse payment =
                    paymentClient.authorize(order.getId(), order.getAmount(), order.getCurrency());
            order.markPaid(payment.paymentId());
            repository.save(order);
            log.info("Order placed order={} payment={}", order.getId(), payment.paymentId());
            return order;
        } catch (PaymentUnavailableException e) {
            order.markFailed(e.getMessage());
            repository.save(order);
            throw e;
        }
    }

    public List<Order> recent(int limit) {
        return repository.findAllByOrderByCreatedAtDesc(Limit.of(limit));
    }
}
