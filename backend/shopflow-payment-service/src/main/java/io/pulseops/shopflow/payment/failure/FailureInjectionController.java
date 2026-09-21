package io.pulseops.shopflow.payment.failure;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Demo-only controls. Mounted under /internal so telemetry ignores them. */
@RestController
@RequestMapping("/internal/failure")
public class FailureInjectionController {

    public record FailureInjectionRequest(
            @Min(0) @Max(100) int errorRate,
            @Min(0) int latencyMs) {
    }

    private final FailureInjector injector;

    public FailureInjectionController(FailureInjector injector) {
        this.injector = injector;
    }

    @GetMapping
    public FailureInjector.Settings current() {
        return injector.current();
    }

    @PostMapping
    public ResponseEntity<FailureInjector.Settings> apply(@Valid @RequestBody FailureInjectionRequest request) {
        injector.apply(request.errorRate(), request.latencyMs());
        return ResponseEntity.ok(injector.current());
    }

    @PostMapping("/reset")
    public ResponseEntity<FailureInjector.Settings> reset() {
        injector.reset();
        return ResponseEntity.ok(injector.current());
    }
}
