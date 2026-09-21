package io.pulseops.api.telemetry;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.pulseops.domain.telemetry.TelemetryEvent;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryIngestionController {

    public record IngestAccepted(String eventId, String correlationId) {
    }

    public record BatchAccepted(int accepted) {
    }

    private final TelemetryIngestionService ingestionService;

    public TelemetryIngestionController(TelemetryIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /** 202, not 201: the event has been accepted for processing, not yet stored anywhere. */
    @PostMapping
    public ResponseEntity<IngestAccepted> ingest(@Valid @RequestBody TelemetryEvent event) {
        TelemetryEvent accepted = ingestionService.ingest(event);
        return ResponseEntity.accepted()
                .body(new IngestAccepted(accepted.eventId(), accepted.correlationId()));
    }

    @PostMapping("/batch")
    public ResponseEntity<BatchAccepted> ingestBatch(@Valid @RequestBody List<@Valid TelemetryEvent> events) {
        events.forEach(ingestionService::ingest);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new BatchAccepted(events.size()));
    }
}
