package io.pulseops.shopflow.common.telemetry;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Emits one HTTP telemetry event per inbound request.
 *
 * <p>Doing this in a filter rather than in each controller means instrumentation cannot be
 * forgotten when a new endpoint is added, and errors thrown by handlers are still measured.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class HttpTelemetryFilter extends OncePerRequestFilter {

    private final TelemetryPublisher publisher;
    private final TelemetryProperties properties;

    public HttpTelemetryFilter(TelemetryPublisher publisher, TelemetryProperties properties) {
        this.publisher = publisher;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return properties.getExcludePaths().stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        long startNanos = System.nanoTime();
        String failureMessage = null;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            failureMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            throw e;
        } finally {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            // An exception escaping the chain means Tomcat will send 500 even though the
            // response object may still read as 200 at this point.
            int status = failureMessage != null ? 500 : response.getStatus();
            publisher.emitHttp(endpointOf(request), status, latencyMs, failureMessage);
        }
    }

    /**
     * Prefers the matched route pattern (/api/orders/{id}) over the raw URI so that telemetry
     * aggregates per endpoint instead of exploding into one series per order id.
     */
    private String endpointOf(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern != null ? pattern.toString() : request.getRequestURI();
    }
}
