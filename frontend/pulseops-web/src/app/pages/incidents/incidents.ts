import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { PulseOpsApi } from '../../core/pulseops-api';
import { IncidentStatus, IncidentSummary } from '../../core/api.types';
import { Badge } from '../../shared/badge';
import { AgoPipe } from '../../shared/format.pipes';

@Component({
  selector: 'app-incidents',
  imports: [RouterLink, Badge, AgoPipe],
  templateUrl: './incidents.html',
  styleUrl: './incidents.scss'
})
export class Incidents {
  private readonly api = inject(PulseOpsApi);

  readonly statuses: (IncidentStatus | 'ALL')[] = ['ALL', 'OPEN', 'MITIGATED', 'RESOLVED'];
  readonly selected = signal<IncidentStatus | 'ALL'>('ALL');
  readonly incidents = signal<IncidentSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  constructor() {
    this.load();
  }

  select(status: IncidentStatus | 'ALL'): void {
    this.selected.set(status);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    const status = this.selected();
    this.api.incidents({ status: status === 'ALL' ? undefined : status, limit: 200 }).subscribe({
      next: value => {
        this.incidents.set(value);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load incidents.');
        this.loading.set(false);
      }
    });
  }
}
