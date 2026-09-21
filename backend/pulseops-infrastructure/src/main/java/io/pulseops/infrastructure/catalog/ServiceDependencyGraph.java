package io.pulseops.infrastructure.catalog;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read model over the Service Catalog's dependency edges.
 *
 * <p>This is what lets PulseOps say "order-service is failing <em>because</em> payment-service is
 * failing" rather than opening two unrelated incidents. Without the graph, correlation could only
 * group alerts by service name and would split a single outage across every service it touched.
 */
@Component
public class ServiceDependencyGraph {

    private final ServiceRepository serviceRepository;
    private final ServiceDependencyRepository dependencyRepository;

    public ServiceDependencyGraph(ServiceRepository serviceRepository,
                                  ServiceDependencyRepository dependencyRepository) {
        this.serviceRepository = serviceRepository;
        this.dependencyRepository = dependencyRepository;
    }

    /** Services that {@code service} calls. */
    @Transactional(readOnly = true)
    public Set<String> dependenciesOf(String service) {
        Set<String> result = new LinkedHashSet<>();
        dependencyRepository.findByKeyServiceName(service)
                .forEach(edge -> result.add(edge.getDependsOnName()));
        return result;
    }

    /** Services that call {@code service}, i.e. the blast radius when it degrades. */
    @Transactional(readOnly = true)
    public Set<String> dependentsOf(String service) {
        Set<String> result = new LinkedHashSet<>();
        dependencyRepository.findByKeyDependsOnName(service)
                .forEach(edge -> result.add(edge.getServiceName()));
        return result;
    }

    /** True when the two services sit on a direct edge, in either direction. */
    @Transactional(readOnly = true)
    public boolean areRelated(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b) || dependenciesOf(a).contains(b) || dependenciesOf(b).contains(a);
    }

    @Transactional(readOnly = true)
    public boolean dependsOn(String service, String candidateDependency) {
        return dependenciesOf(service).contains(candidateDependency);
    }

    @Transactional(readOnly = true)
    public String displayNameOf(String service) {
        return serviceRepository.findById(service)
                .map(ServiceEntity::getDisplayName)
                .orElseGet(() -> prettify(service));
    }

    /** Fallback for a service that emits telemetry but is not in the catalog yet. */
    private static String prettify(String service) {
        String[] parts = service.split("[-_]");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }
}
