package io.pulseops.api.incident;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import io.pulseops.api.incident.IncidentViews.AlertView;
import io.pulseops.api.incident.IncidentViews.IncidentDetail;
import io.pulseops.api.incident.IncidentViews.IncidentSummary;
import io.pulseops.api.incident.IncidentViews.TimelineEntry;
import io.pulseops.domain.IncidentStatus;
import io.pulseops.infrastructure.alert.AlertRepository;
import io.pulseops.infrastructure.incident.IncidentEntity;
import io.pulseops.infrastructure.incident.IncidentRepository;
import io.pulseops.infrastructure.incident.IncidentTimelineRepository;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentRepository incidentRepository;
    private final IncidentTimelineRepository timelineRepository;
    private final AlertRepository alertRepository;

    public IncidentController(IncidentRepository incidentRepository,
                              IncidentTimelineRepository timelineRepository,
                              AlertRepository alertRepository) {
        this.incidentRepository = incidentRepository;
        this.timelineRepository = timelineRepository;
        this.alertRepository = alertRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<IncidentSummary> list(@RequestParam(required = false) IncidentStatus status,
                                      @RequestParam(defaultValue = "50") int limit) {
        List<IncidentEntity> incidents = status != null
                ? incidentRepository.findByStatusOrderByOpenedAtDesc(status)
                : incidentRepository.findAllByOrderByOpenedAtDesc(Limit.of(Math.clamp(limit, 1, 200)));
        return incidents.stream().map(IncidentSummary::from).toList();
    }

    @GetMapping("/{incidentKey}")
    @Transactional(readOnly = true)
    public IncidentDetail get(@PathVariable UUID incidentKey) {
        IncidentEntity incident = incidentRepository.findById(incidentKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown incident"));

        List<AlertView> alerts = alertRepository.findByIncidentKeyOrderByOccurredAtAsc(incidentKey).stream()
                .map(AlertView::from)
                .toList();
        List<TimelineEntry> timeline = timelineRepository.findByIncidentKeyOrderByOccurredAtAsc(incidentKey).stream()
                .map(TimelineEntry::from)
                .toList();

        return new IncidentDetail(IncidentSummary.from(incident), alerts, timeline);
    }

    @GetMapping("/{incidentKey}/timeline")
    @Transactional(readOnly = true)
    public List<TimelineEntry> timeline(@PathVariable UUID incidentKey) {
        return timelineRepository.findByIncidentKeyOrderByOccurredAtAsc(incidentKey).stream()
                .map(TimelineEntry::from)
                .toList();
    }

    @GetMapping("/{incidentKey}/alerts")
    @Transactional(readOnly = true)
    public List<AlertView> alerts(@PathVariable UUID incidentKey) {
        return alertRepository.findByIncidentKeyOrderByOccurredAtAsc(incidentKey).stream()
                .map(AlertView::from)
                .toList();
    }
}
