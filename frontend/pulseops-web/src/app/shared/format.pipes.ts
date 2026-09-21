import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'ago' })
export class AgoPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (!value) {
      return '—';
    }
    const seconds = Math.round((Date.now() - new Date(value).getTime()) / 1000);
    if (seconds < 60) {
      return `${Math.max(seconds, 0)}s ago`;
    }
    if (seconds < 3600) {
      return `${Math.floor(seconds / 60)}m ago`;
    }
    if (seconds < 86400) {
      return `${Math.floor(seconds / 3600)}h ago`;
    }
    return `${Math.floor(seconds / 86400)}d ago`;
  }
}

@Pipe({ name: 'clock' })
export class ClockPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (!value) {
      return '—';
    }
    return new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  }
}

@Pipe({ name: 'duration' })
export class DurationPipe implements PipeTransform {
  transform(ms: number | null | undefined): string {
    if (ms == null) {
      return '—';
    }
    return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`;
  }
}

/**
 * An alert's units depend on which metric fired, and the metric is not carried on the alert view.
 * Rendering a bare number invites reading an error rate of 0.25 as a latency, so a value without a
 * known unit is shown as-is rather than dressed up.
 */
@Pipe({ name: 'metricValue' })
export class MetricValuePipe implements PipeTransform {
  transform(value: number | null | undefined, alertType: string | null | undefined): string {
    if (value == null) {
      return '—';
    }
    const type = (alertType ?? '').toLowerCase();
    if (type.includes('latency')) {
      return `${Math.round(value)}ms`;
    }
    if (type.includes('rate') || type.includes('error')) {
      return `${(value * 100).toFixed(1)}%`;
    }
    return `${value}`;
  }
}
