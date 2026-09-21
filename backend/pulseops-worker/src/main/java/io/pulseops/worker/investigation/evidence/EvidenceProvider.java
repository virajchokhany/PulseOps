package io.pulseops.worker.investigation.evidence;

/**
 * One source of investigation evidence.
 *
 * <p>Each source is a separate implementation so the AI worker orchestrates collection rather than
 * containing every query itself, and so a new evidence source can be added without touching the
 * worker. Each provider is self-contained: it runs its own queries instead of receiving another
 * provider's output, which keeps them independently testable at the cost of a little repeated
 * querying.
 */
public interface EvidenceProvider<T> {

    String name();

    T collect(EvidenceScope scope);
}
