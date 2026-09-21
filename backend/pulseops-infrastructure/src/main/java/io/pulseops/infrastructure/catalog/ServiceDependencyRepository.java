package io.pulseops.infrastructure.catalog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceDependencyRepository
        extends JpaRepository<ServiceDependencyEntity, ServiceDependencyEntity.Key> {

    /** What this service calls. */
    List<ServiceDependencyEntity> findByKeyServiceName(String serviceName);

    /** What calls this service. */
    List<ServiceDependencyEntity> findByKeyDependsOnName(String dependsOnName);
}
