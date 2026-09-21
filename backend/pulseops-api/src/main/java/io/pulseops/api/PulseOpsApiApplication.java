package io.pulseops.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Telemetry ingestion, the Incident Store consumer and the REST API for the Angular dashboard.
 *
 * <p>This application also owns the Flyway migrations for the whole platform; every other service
 * runs with {@code spring.flyway.enabled=false} so exactly one process migrates the schema.
 */
@SpringBootApplication(scanBasePackages = { "io.pulseops.api", "io.pulseops.infrastructure" })
@EntityScan(basePackages = "io.pulseops.infrastructure")
@EnableJpaRepositories(basePackages = "io.pulseops.infrastructure")
public class PulseOpsApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PulseOpsApiApplication.class, args);
    }
}
