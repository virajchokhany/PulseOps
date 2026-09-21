export type Severity = 'INFO' | 'WARNING' | 'MAJOR' | 'CRITICAL';
export type IncidentStatus = 'OPEN' | 'MITIGATED' | 'RESOLVED';
export type RcaStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'STALE' | 'FAILED';
export type TelemetryType = 'HTTP' | 'DEPENDENCY' | 'BUSINESS';
export type AlertMetric = 'ERROR_RATE' | 'AVG_LATENCY_MS' | 'P95_LATENCY_MS';

export interface IncidentSummary {
  incidentKey: string;
  incidentNumber: number;
  title: string;
  status: IncidentStatus;
  severity: Severity;
  primaryService: string;
  affectedServices: string[];
  alertCount: number;
  rcaStatus: RcaStatus;
  openedAt: string;
  updatedAt: string;
}

export interface AlertView {
  alertId: string;
  service: string;
  alertType: string;
  severity: string;
  description: string;
  metricValue: number | null;
  threshold: number | null;
  sampleCount: number | null;
  occurredAt: string;
  incidentKey: string;
}

export interface TimelineEntry {
  occurredAt: string;
  entryType: string;
  summary: string;
  correlationId: string | null;
}

export interface IncidentDetail {
  incident: IncidentSummary;
  alerts: AlertView[];
  timeline: TimelineEntry[];
}

export interface ServiceView {
  name: string;
  displayName: string;
  owner: string;
  repository: string;
  environment: string;
  version: string;
  tier: string;
  description: string;
  dependsOn: string[];
  dependedOnBy: string[];
}

export interface TimelinePoint {
  at: string;
  description: string;
}

export interface SuspectedDeployment {
  service: string;
  version: string;
  commitSha: string;
  deployedAt: string;
  minutesBeforeIncident: number;
  why: string;
}

export interface RelevantPullRequest {
  number: number;
  title: string;
  url: string;
  commitSha: string;
  why: string;
}

export interface RelevantCode {
  filePath: string;
  service: string;
  startLine: number;
  endLine: number;
  why: string;
}

export interface RootCauseAnalysis {
  summary: string;
  probableRootCause: string;
  confidence: number;
  evidence: string[];
  timeline: TimelinePoint[];
  affectedServices: string[];
  suspectedDeployment: SuspectedDeployment | null;
  relevantPullRequests: RelevantPullRequest[];
  relevantCode: RelevantCode[];
  recommendedActions: string[];
  uncertainties: string[];
  generatedAt: string;
  provider: string;
  model: string;
}

export interface ServiceTelemetrySummary {
  service: string;
  eventType: TelemetryType;
  endpoints: string[];
  from: string;
  to: string;
  sampleCount: number;
  errorCount: number;
  errorRate: number;
  avgLatencyMs: number;
  p95LatencyMs: number;
  statusCodeCounts: Record<string, number>;
  sampleErrorMessages: string[];
  deploymentVersionsObserved: string[];
}

export interface CodeSnippet {
  service: string;
  repository: string;
  filePath: string;
  commitSha: string;
  startLine: number;
  endLine: number;
  content: string;
  reason: string;
}

export interface PullRequestView {
  number: number;
  repository: string;
  title: string;
  description: string;
  author: string;
  commitSha: string;
  mergedAt: string;
  url: string;
  changedFiles: string[];
}

export interface DeploymentView {
  service: string;
  version: string;
  commitSha: string;
  environment: string;
  deployedBy: string;
  deployedAt: string;
  status: string;
  notes: string | null;
  minutesBeforeIncident: number;
}

export interface RunbookView {
  service: string;
  alertType: string;
  title: string;
  content: string;
}

/** Counts of what the investigation was given, plus the gaps it was told about. */
export interface EvidenceSummary {
  alerts: number;
  telemetryGroups: number;
  deployments: number;
  pullRequests: number;
  codeSnippets: number;
  runbooks: number;
  missingEvidence: string[];
}

export interface InvestigationAttempt {
  id: number;
  status: string;
  provider: string;
  model: string;
  startedAt: string;
  completedAt: string | null;
  durationMs: number;
  failureReason: string | null;
}

export interface RcaResponse {
  incidentKey: string;
  rcaStatus: RcaStatus;
  rca: RootCauseAnalysis | null;
  evidenceSummary: EvidenceSummary | null;
  history: InvestigationAttempt[];
}
