import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { PulseOpsApi } from '../../core/pulseops-api';
import { AlertView, IncidentSummary, ServiceView } from '../../core/api.types';
import { Badge } from '../../shared/badge';
import { AgoPipe, MetricValuePipe } from '../../shared/format.pipes';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, Badge, AgoPipe, MetricValuePipe],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss'
})
export class Dashboard {
  private readonly api = inject(PulseOpsApi);

  readonly incidents = signal<IncidentSummary[]>([]);
  readonly alerts = signal<AlertView[]>([]);
  readonly services = signal<ServiceView[]>([]);
  readonly error = signal<string | null>(null);
  readonly loading = signal(true);

  readonly openIncidents = computed(() => this.incidents().filter(i => i.status === 'OPEN'));
  readonly criticalCount = computed(() => this.openIncidents().filter(i => i.severity === 'CRITICAL').length);
  readonly investigating = computed(() =>
    this.incidents().filter(i => i.rcaStatus === 'IN_PROGRESS' || i.rcaStatus === 'NOT_STARTED').length
  );
  readonly recentIncidents = computed(() => this.incidents().slice(0, 6));
  readonly recentAlerts = computed(() => this.alerts().slice(0, 8));

  constructor() {
    this.api.incidents({ limit: 50 }).subscribe({
      next: value => {
        this.incidents.set(value);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not reach the PulseOps API.');
        this.loading.set(false);
      }
    });
    this.api.alerts(20).subscribe({ next: value => this.alerts.set(value), error: () => undefined });
    this.api.services().subscribe({ next: value => this.services.set(value), error: () => undefined });
  }
}
