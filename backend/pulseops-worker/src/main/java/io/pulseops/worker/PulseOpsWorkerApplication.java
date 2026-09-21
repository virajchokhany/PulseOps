package io.pulseops.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Hosts the event-driven components: Telemetry Processor, Alert Engine, Incident Correlation and
 * the AI Investigation Worker.
 *
 * <p>They share a process because they are small and deploy together, not because they are coupled.
 * Each is a separate Kafka consumer group, so splitting one out later is a packaging change rather
 * than a redesign.
 */
@SpringBootApplication(scanBasePackages = { "io.pulseops.worker", "io.pulseops.infrastructure" })
@EntityScan(basePackages = "io.pulseops.infrastructure")
@EnableJpaRepositories(basePackages = "io.pulseops.infrastructure")
public class PulseOpsWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PulseOpsWorkerApplication.class, args);
    }
}
