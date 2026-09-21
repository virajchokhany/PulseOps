import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { PulseOpsApi } from '../../core/pulseops-api';
import { IncidentDetail, RcaResponse, ServiceTelemetrySummary } from '../../core/api.types';
import { Badge } from '../../shared/badge';
import { AgoPipe, ClockPipe, DurationPipe, MetricValuePipe } from '../../shared/format.pipes';

/** No analysis to show yet, so the page shows a spinner. */
const PENDING_RCA_STATUSES = ['NOT_STARTED', 'IN_PROGRESS'];
/** An investigation is running. STALE belongs here: the old RCA is shown while a new one is built. */
const ACTIVE_RCA_STATUSES = ['NOT_STARTED', 'IN_PROGRESS', 'STALE'];
const ACTIVE_POLL_MS = 5000;
const IDLE_POLL_MS = 20000;

@Component({
  selector: 'app-incident-detail',
  imports: [RouterLink, Badge, AgoPipe, ClockPipe, DurationPipe, MetricValuePipe],
  templateUrl: './incident-detail.html',
  styleUrl: './incident-detail.scss'
})
export class IncidentDetailPage implements OnInit {
  private readonly api = inject(PulseOpsApi);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  private incidentKey = '';
  private timer: ReturnType<typeof setTimeout> | null = null;

  readonly detail = signal<IncidentDetail | null>(null);
  readonly rca = signal<RcaResponse | null>(null);
  readonly telemetry = signal<ServiceTelemetrySummary[]>([]);
  readonly error = signal<string | null>(null);
  readonly loading = signal(true);

  readonly incident = computed(() => this.detail()?.incident ?? null);
  readonly analysis = computed(() => this.rca()?.rca ?? null);
  readonly evidence = computed(() => this.rca()?.evidenceSummary ?? null);
  readonly awaitingRca = computed(() => {
    const status = this.rca()?.rcaStatus;
    return !!status && PENDING_RCA_STATUSES.includes(status);
  });
  readonly lastAttempt = computed(() => this.rca()?.history?.[0] ?? null);
  readonly confidencePercent = computed(() => {
    const value = this.analysis()?.confidence;
    return value == null ? null : Math.round(value * 100);
  });

  ngOnInit(): void {
    this.incidentKey = this.route.snapshot.paramMap.get('key') ?? '';
    this.destroyRef.onDestroy(() => this.stopPolling());
    this.loadDetail();
    this.loadRca();
    this.loadTelemetry();
  }

  private loadDetail(): void {
    this.api.incident(this.incidentKey).subscribe({
      next: value => {
        this.detail.set(value);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Incident not found.');
        this.loading.set(false);
      }
    });
  }

  /**
   * The investigation is triggered by the incident pipeline, not by the operator, so the page has
   * to discover the result on its own. It keeps polling after the RCA completes, because a live
   * incident can pick up new alerts and be re-investigated while someone is reading this page.
   */
  private loadRca(): void {
    this.api.rca(this.incidentKey).subscribe({
      next: value => {
        const previous = this.rca()?.rcaStatus;
        this.rca.set(value);
        if (previous && previous !== value.rcaStatus) {
          this.loadDetail();
          this.loadTelemetry();
        }
        const active = ACTIVE_RCA_STATUSES.includes(value.rcaStatus);
        this.timer = setTimeout(() => this.loadRca(), active ? ACTIVE_POLL_MS : IDLE_POLL_MS);
      },
      error: () => this.stopPolling()
    });
  }

  private loadTelemetry(): void {
    this.api.incidentTelemetry(this.incidentKey).subscribe({
      next: value => this.telemetry.set(value),
      error: () => undefined
    });
  }

  private stopPolling(): void {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }
}
