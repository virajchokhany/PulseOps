package io.pulseops.api.catalog;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.pulseops.infrastructure.catalog.ServiceDependencyGraph;
import io.pulseops.infrastructure.catalog.ServiceEntity;
import io.pulseops.infrastructure.catalog.ServiceRepository;

@RestController
@RequestMapping("/api/services")
public class ServiceCatalogController {

    public record ServiceView(
            String name,
            String displayName,
            String owner,
            String repository,
            String environment,
            String version,
            String tier,
            String description,
            Set<String> dependsOn,
            Set<String> dependedOnBy) {
    }

    private final ServiceRepository serviceRepository;
    private final ServiceDependencyGraph dependencyGraph;

    public ServiceCatalogController(ServiceRepository serviceRepository, ServiceDependencyGraph dependencyGraph) {
        this.serviceRepository = serviceRepository;
        this.dependencyGraph = dependencyGraph;
    }

    @GetMapping
    public List<ServiceView> list() {
        return serviceRepository.findAllByOrderByNameAsc().stream().map(this::toView).toList();
    }

    @GetMapping("/{name}")
    public ServiceView get(@PathVariable String name) {
        return serviceRepository.findById(name)
                .map(this::toView)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown service"));
    }

    private ServiceView toView(ServiceEntity service) {
        return new ServiceView(
                service.getName(), service.getDisplayName(), service.getOwner(), service.getRepository(),
                service.getEnvironment(), service.getVersion(), service.getTier(), service.getDescription(),
                dependencyGraph.dependenciesOf(service.getName()),
                dependencyGraph.dependentsOf(service.getName()));
    }
}
