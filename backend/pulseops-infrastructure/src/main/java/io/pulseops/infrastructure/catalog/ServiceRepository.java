package io.pulseops.infrastructure.catalog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRepository extends JpaRepository<ServiceEntity, String> {

    List<ServiceEntity> findAllByOrderByNameAsc();
}
