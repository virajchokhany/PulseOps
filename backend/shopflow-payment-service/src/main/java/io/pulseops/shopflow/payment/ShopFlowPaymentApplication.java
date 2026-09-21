package io.pulseops.shopflow.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** ShopFlow Payment Service: the service the PulseOps demo scenario degrades on purpose. */
@SpringBootApplication(scanBasePackages = { "io.pulseops.shopflow.payment", "io.pulseops.shopflow.common" })
public class ShopFlowPaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopFlowPaymentApplication.class, args);
    }
}
