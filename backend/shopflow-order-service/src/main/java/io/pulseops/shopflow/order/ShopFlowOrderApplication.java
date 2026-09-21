package io.pulseops.shopflow.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** ShopFlow Order Service: the upstream that shows payment degradation as order failures. */
@SpringBootApplication(scanBasePackages = { "io.pulseops.shopflow.order", "io.pulseops.shopflow.common" })
public class ShopFlowOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopFlowOrderApplication.class, args);
    }
}
