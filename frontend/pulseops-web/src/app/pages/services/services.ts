import { Component, inject, signal } from '@angular/core';

import { PulseOpsApi } from '../../core/pulseops-api';
import { ServiceView } from '../../core/api.types';

@Component({
  selector: 'app-services',
  templateUrl: './services.html',
  styleUrl: './services.scss'
})
export class Services {
  private readonly api = inject(PulseOpsApi);

  readonly services = signal<ServiceView[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  constructor() {
    this.api.services().subscribe({
      next: value => {
        this.services.set(value);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load the service catalog.');
        this.loading.set(false);
      }
    });
  }
}
