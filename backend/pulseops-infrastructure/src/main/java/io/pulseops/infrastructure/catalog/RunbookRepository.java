package io.pulseops.infrastructure.catalog;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RunbookRepository extends JpaRepository<RunbookEntity, Long> {

    List<RunbookEntity> findByServiceNameIn(Collection<String> serviceNames);
}
