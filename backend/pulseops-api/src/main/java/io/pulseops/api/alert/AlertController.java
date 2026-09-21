package io.pulseops.api.alert;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.pulseops.api.incident.IncidentViews.AlertView;
import io.pulseops.infrastructure.alert.AlertRepository;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertRepository alertRepository;

    public AlertController(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @GetMapping
    public List<AlertView> list(@RequestParam(defaultValue = "50") int limit) {
        return alertRepository.findAllByOrderByOccurredAtDesc(Limit.of(Math.clamp(limit, 1, 200))).stream()
                .map(AlertView::from)
                .toList();
    }
}
