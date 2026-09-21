import { Component, input } from '@angular/core';

@Component({
  selector: 'app-badge',
  template: `<span class="badge" [class]="'badge--' + (value() || 'unknown').toLowerCase()">{{ label() }}</span>`,
  styleUrl: './badge.scss'
})
export class Badge {
  readonly value = input.required<string | null | undefined>();

  label(): string {
    return (this.value() ?? 'UNKNOWN').replace(/_/g, ' ');
  }
}
