import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from './api-base-url';
import {
  AlertView,
  IncidentDetail,
  IncidentStatus,
  IncidentSummary,
  RcaResponse,
  ServiceTelemetrySummary,
  ServiceView,
  TimelineEntry
} from './api.types';

@Injectable({ providedIn: 'root' })
export class PulseOpsApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  incidents(options: { status?: IncidentStatus; limit?: number } = {}): Observable<IncidentSummary[]> {
    let params = new HttpParams();
    if (options.status) {
      params = params.set('status', options.status);
    }
    if (options.limit) {
      params = params.set('limit', options.limit);
    }
    return this.http.get<IncidentSummary[]>(`${this.base}/incidents`, { params });
  }

  incident(incidentKey: string): Observable<IncidentDetail> {
    return this.http.get<IncidentDetail>(`${this.base}/incidents/${incidentKey}`);
  }

  timeline(incidentKey: string): Observable<TimelineEntry[]> {
    return this.http.get<TimelineEntry[]>(`${this.base}/incidents/${incidentKey}/timeline`);
  }

  incidentAlerts(incidentKey: string): Observable<AlertView[]> {
    return this.http.get<AlertView[]>(`${this.base}/incidents/${incidentKey}/alerts`);
  }

  rca(incidentKey: string): Observable<RcaResponse> {
    return this.http.get<RcaResponse>(`${this.base}/incidents/${incidentKey}/rca`);
  }

  incidentTelemetry(incidentKey: string): Observable<ServiceTelemetrySummary[]> {
    return this.http.get<ServiceTelemetrySummary[]>(`${this.base}/incidents/${incidentKey}/telemetry`);
  }

  alerts(limit = 50): Observable<AlertView[]> {
    return this.http.get<AlertView[]>(`${this.base}/alerts`, {
      params: new HttpParams().set('limit', limit)
    });
  }

  services(): Observable<ServiceView[]> {
    return this.http.get<ServiceView[]>(`${this.base}/services`);
  }

  service(name: string): Observable<ServiceView> {
    return this.http.get<ServiceView>(`${this.base}/services/${encodeURIComponent(name)}`);
  }
}
