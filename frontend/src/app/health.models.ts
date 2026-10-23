export interface IntegrationHealth {
  integration: string;
  status: 'HEALTHY' | 'DEGRADED' | 'UNHEALTHY' | 'INSUFFICIENT DATA' | 'NO DATA';
  requests: number;
  failures: number;
  errorRatePct: number | null;
  successP95LatencyMs: number | null;
  failureBreakdown: Record<string, number>;
  reason: string;
}

export interface HealthReport {
  asOf: string | null;
  windowMinutes: number;
  overallStatus: IntegrationHealth['status'];
  integrations: IntegrationHealth[];
}
