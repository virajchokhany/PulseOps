import { InjectionToken } from '@angular/core';

declare global {
  interface Window {
    PULSEOPS_CONFIG?: { apiBaseUrl?: string };
  }
}

/**
 * Read at runtime rather than baked in at build time, so the same container image can be promoted
 * between environments. The default is relative, which keeps the browser on one origin: in
 * development the dev-server proxy forwards it, and in production a reverse proxy or the hosting
 * platform's routing does. Nothing here knows about localhost or a port.
 */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => window.PULSEOPS_CONFIG?.apiBaseUrl?.replace(/\/$/, '') || '/api'
});
