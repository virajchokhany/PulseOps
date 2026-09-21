package io.pulseops.worker.investigation.evidence;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.pulseops.domain.evidence.RunbookView;
import io.pulseops.infrastructure.catalog.RunbookRepository;

/**
 * Supplies the operator runbooks for the affected services.
 *
 * <p>These encode what the owning team already knows about this failure mode, which is exactly the
 * institutional context a model has no other way to acquire.
 */
@Component
public class RunbookEvidenceProvider implements EvidenceProvider<List<RunbookView>> {

    private final RunbookRepository repository;

    public RunbookEvidenceProvider(RunbookRepository repository) {
        this.repository = repository;
    }

    @Override
    public String name() {
        return "runbooks";
    }

    @Override
    @Transactional(readOnly = true)
    public List<RunbookView> collect(EvidenceScope scope) {
        return repository.findByServiceNameIn(scope.affectedServices()).stream()
                // A runbook tied to an alert type is only relevant if that alert actually fired.
                .filter(runbook -> runbook.getAlertType() == null
                        || scope.alertTypes().contains(runbook.getAlertType()))
                .map(runbook -> new RunbookView(
                        runbook.getServiceName(), runbook.getAlertType(),
                        runbook.getTitle(), runbook.getContent()))
                .toList();
    }
}
