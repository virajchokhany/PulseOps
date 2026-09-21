package io.pulseops.worker.investigation.evidence;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.pulseops.domain.evidence.ServiceView;
import io.pulseops.infrastructure.catalog.ServiceDependencyGraph;
import io.pulseops.infrastructure.catalog.ServiceEntity;
import io.pulseops.infrastructure.catalog.ServiceRepository;

/** Supplies ownership and the dependency edges that explain why services failed together. */
@Component
public class ServiceCatalogEvidenceProvider implements EvidenceProvider<List<ServiceView>> {

    private final ServiceRepository serviceRepository;
    private final ServiceDependencyGraph dependencyGraph;

    public ServiceCatalogEvidenceProvider(ServiceRepository serviceRepository,
                                          ServiceDependencyGraph dependencyGraph) {
        this.serviceRepository = serviceRepository;
        this.dependencyGraph = dependencyGraph;
    }

    @Override
    public String name() {
        return "services";
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceView> collect(EvidenceScope scope) {
        List<ServiceView> views = new ArrayList<>();
        for (String service : scope.affectedServices()) {
            ServiceEntity entity = serviceRepository.findById(service).orElse(null);
            if (entity == null) {
                continue;
            }
            views.add(new ServiceView(
                    entity.getName(), entity.getDisplayName(), entity.getOwner(), entity.getRepository(),
                    entity.getEnvironment(), entity.getVersion(), entity.getTier(), entity.getDescription(),
                    List.copyOf(dependencyGraph.dependenciesOf(service)),
                    List.copyOf(dependencyGraph.dependentsOf(service))));
        }
        return views;
    }
}
