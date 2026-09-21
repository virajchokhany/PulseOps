package io.pulseops.shopflow.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.pulseops.domain.CorrelationId;
import io.pulseops.shopflow.payment.api.PaymentRequest;
import io.pulseops.shopflow.payment.client.PaymentProviderClient;
import io.pulseops.shopflow.payment.client.PaymentProviderException;
import io.pulseops.shopflow.payment.client.PaymentProviderTimeoutException;
import io.pulseops.shopflow.payment.domain.Payment;
import io.pulseops.shopflow.payment.domain.PaymentRepository;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentProviderClient providerClient;
    private final PaymentRepository repository;

    public PaymentService(PaymentProviderClient providerClient, PaymentRepository repository) {
        this.providerClient = providerClient;
        this.repository = repository;
    }

    public Payment authorize(PaymentRequest request) {
        String correlationId = CorrelationId.current();
        String currency = request.currencyOrDefault();
        try {
            PaymentProviderClient.ProviderAuthorization authorization =
                    providerClient.authorize(request.orderId(), request.amount(), currency);
            Payment payment = Payment.approved(
                    request.orderId(), request.amount(), currency, authorization.reference(), correlationId);
            return repository.save(payment);
        } catch (PaymentProviderTimeoutException e) {
            // Recorded before rethrowing: a failed payment is a business fact, not just an error.
            recordFailure(request, currency, correlationId, e.getMessage());
            log.error("Payment authorisation timed out order={} waitedMs={}", request.orderId(), e.getWaitedMs());
            throw e;
        } catch (PaymentProviderException e) {
            recordFailure(request, currency, correlationId, e.getMessage());
            log.error("Payment authorisation failed order={} reason={}", request.orderId(), e.getMessage());
            throw e;
        }
    }

    private void recordFailure(PaymentRequest request, String currency, String correlationId, String reason) {
        repository.save(Payment.failed(request.orderId(), request.amount(), currency, reason, correlationId));
    }
}
