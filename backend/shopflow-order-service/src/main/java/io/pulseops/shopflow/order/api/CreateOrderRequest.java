package io.pulseops.shopflow.order.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateOrderRequest(
        @NotBlank String customerId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @Pattern(regexp = "[A-Z]{3}") String currency) {

    public String currencyOrDefault() {
        return currency == null || currency.isBlank() ? "USD" : currency;
    }
}
