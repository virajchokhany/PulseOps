package io.pulseops.shopflow.common;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import io.pulseops.shopflow.common.correlation.CorrelationIdFilter;
import io.pulseops.shopflow.common.telemetry.HttpTelemetryFilter;
import io.pulseops.shopflow.common.telemetry.TelemetryProperties;
import io.pulseops.shopflow.common.telemetry.TelemetryPublisher;

/** Wires correlation-id propagation and telemetry emission into any ShopFlow service. */
@Configuration
@EnableConfigurationProperties(TelemetryProperties.class)
public class ShopFlowCommonConfiguration {

    @Bean
    RestClient telemetryRestClient(TelemetryProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getIngestUrl())
                .requestFactory(requestFactory(properties.getConnectTimeout(), properties.getReadTimeout()))
                .build();
    }

    @Bean
    TelemetryPublisher telemetryPublisher(TelemetryProperties properties,
                                          @Qualifier("telemetryRestClient") RestClient telemetryRestClient) {
        return new TelemetryPublisher(properties, telemetryRestClient);
    }

    @Bean
    CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    @Bean
    HttpTelemetryFilter httpTelemetryFilter(TelemetryPublisher publisher, TelemetryProperties properties) {
        return new HttpTelemetryFilter(publisher, properties);
    }

    static SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) connectTimeout.toMillis());
        factory.setReadTimeout((int) readTimeout.toMillis());
        return factory;
    }
}
