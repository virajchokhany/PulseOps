package io.pulseops.infrastructure.telemetry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.pulseops.domain.evidence.ServiceTelemetrySummary;

/**
 * Aggregates the telemetry a set of services produced over a window.
 *
 * <p>Summaries are produced per service <em>and</em> per event type, so inbound HTTP behaviour and
 * outbound dependency behaviour stay separate. That separation is what lets an analysis distinguish
 * a service that is itself broken from one that is merely waiting on something else.
 *
 * <p>This sits in the infrastructure layer rather than beside the investigation code because two
 * callers need it: the worker, which feeds it to the model, and the API, which shows an operator
 * what the model was looking at. A second implementation would eventually disagree with the first,
 * and the page would then be quietly misrepresenting the evidence behind the analysis.
 */
@Component
public class TelemetrySummarizer {

    private static final int MAX_ROWS_PER_SERVICE = 5000;
    private static final int MAX_SAMPLE_MESSAGES = 5;
    private static final int MAX_ENDPOINTS = 10;
    private static final int MAX_MESSAGE_CHARS = 200;

    /**
     * Error messages often embed a whole response body whose only varying part is a correlation id
     * or timestamp. Those are normalised away before deduplication so the samples show the distinct
     * failure <em>modes</em> rather than N copies of one mode.
     */
    private static final Pattern VOLATILE_TOKENS = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
                    + "|\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z?");

    private final TelemetryEventRepository repository;

    public TelemetrySummarizer(TelemetryEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ServiceTelemetrySummary> summarise(Collection<String> services, Instant from, Instant to) {
        List<ServiceTelemetrySummary> summaries = new ArrayList<>();

        for (String service : services) {
            List<TelemetryEventEntity> events = repository
                    .findByServiceAndOccurredAtBetweenOrderByOccurredAtDesc(
                            service, from, to, Limit.of(MAX_ROWS_PER_SERVICE));
            if (events.isEmpty()) {
                continue;
            }
            events.stream()
                    .collect(Collectors.groupingBy(TelemetryEventEntity::getEventType))
                    .forEach((eventType, grouped) -> summaries.add(summarise(service, eventType, grouped, from, to)));
        }
        return summaries;
    }

    private ServiceTelemetrySummary summarise(String service, String eventType,
                                              List<TelemetryEventEntity> events, Instant from, Instant to) {
        int errorCount = 0;
        long latencySum = 0;
        int latencyCount = 0;
        int[] latencies = new int[events.size()];
        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        List<String> sampleMessages = new ArrayList<>();
        List<String> versions = new ArrayList<>();
        List<String> endpoints = new ArrayList<>();

        for (TelemetryEventEntity event : events) {
            Integer status = event.getStatusCode();
            if (status != null) {
                statusCounts.merge(String.valueOf(status), 1, Integer::sum);
                if (status >= 500) {
                    errorCount++;
                    String message = truncate(event.getMessage());
                    if (message != null && sampleMessages.size() < MAX_SAMPLE_MESSAGES
                            && !sampleMessages.contains(message)) {
                        sampleMessages.add(message);
                    }
                }
            }
            if (event.getLatencyMs() != null) {
                latencies[latencyCount++] = event.getLatencyMs();
                latencySum += event.getLatencyMs();
            }
            if (event.getDeploymentVersion() != null && !versions.contains(event.getDeploymentVersion())) {
                versions.add(event.getDeploymentVersion());
            }
            if (event.getEndpoint() != null && endpoints.size() < MAX_ENDPOINTS
                    && !endpoints.contains(event.getEndpoint())) {
                endpoints.add(event.getEndpoint());
            }
        }

        double avgLatency = latencyCount == 0 ? 0 : (double) latencySum / latencyCount;
        double p95 = 0;
        if (latencyCount > 0) {
            int[] sorted = Arrays.copyOf(latencies, latencyCount);
            Arrays.sort(sorted);
            p95 = sorted[Math.min(latencyCount - 1, Math.max(0, (int) Math.ceil(latencyCount * 0.95) - 1))];
        }

        return new ServiceTelemetrySummary(
                service, eventType, endpoints, from, to,
                events.size(), errorCount, (double) errorCount / events.size(),
                avgLatency, p95, statusCounts, sampleMessages, versions);
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        String normalised = VOLATILE_TOKENS.matcher(message.replaceAll("\\s+", " ").trim())
                .replaceAll("<redacted>");
        return normalised.length() <= MAX_MESSAGE_CHARS
                ? normalised
                : normalised.substring(0, MAX_MESSAGE_CHARS) + "...";
    }
}
