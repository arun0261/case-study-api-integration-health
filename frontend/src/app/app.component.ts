import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HealthApiService } from './health-api.service';
import { HealthReport, IntegrationHealth } from './health.models';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <main class="shell">
      <header class="topbar">
        <a class="brand" href="/" aria-label="Brightline home">
          <span class="brand-mark">b</span>
          <span>brightline<span class="brand-muted"> / platform ops</span></span>
        </a>
        <span class="environment"><span class="live-dot"></span> INTEGRATION MONITOR</span>
      </header>

      <section class="intro">
        <div>
          <p class="eyebrow">OPERATIONS OVERVIEW</p>
          <h1>Integration health</h1>
          <p class="subtitle">A clear picture of the systems your customers depend on.</p>
        </div>
        <div class="toolbar">
          <label for="window">Time window</label>
          <select id="window" [(ngModel)]="windowMinutes" (ngModelChange)="refresh()">
            <option [ngValue]="30">Last 30 minutes</option>
            <option [ngValue]="60">Last 60 minutes</option>
            <option [ngValue]="120">Last 2 hours</option>
          </select>
          <button class="refresh" type="button" (click)="refresh()" [disabled]="loading">
            <span [class.spinning]="loading">↻</span> Refresh
          </button>
        </div>
      </section>

      <section class="summary-row" *ngIf="report as data">
        <div class="overall-card" [ngClass]="statusClass(data.overallStatus)">
          <div class="overall-icon">{{ statusIcon(data.overallStatus) }}</div>
          <div>
            <div class="card-label">OVERALL STATUS</div>
            <div class="overall-status">{{ data.overallStatus }}</div>
            <div class="overall-note">{{ overallNote(data) }}</div>
          </div>
        </div>
        <div class="stat-card">
          <span class="stat-label">INTEGRATIONS</span>
          <strong>{{ data.integrations.length }}</strong>
          <span class="stat-note">being monitored</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">NEED ATTENTION</span>
          <strong [class.attention]="attentionCount(data) > 0">{{ attentionCount(data) }}</strong>
          <span class="stat-note">degraded or unhealthy</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">WINDOW ENDS</span>
          <strong class="time-value">{{ data.asOf ? (data.asOf | date:'HH:mm') : '—' }}</strong>
          <span class="stat-note">{{ data.asOf ? (data.asOf | date:'MMM d, y') : 'No events' }}</span>
        </div>
      </section>

      <div class="error-banner" *ngIf="error" role="alert">
        <span class="error-icon">!</span><span>{{ error }}</span>
        <button type="button" (click)="refresh()">Try again</button>
      </div>

      <section class="integrations-section">
        <div class="section-heading">
          <div>
            <h2>Connected systems</h2>
            <p>Health is based on recent request outcomes and successful-request latency.</p>
          </div>
          <span class="updated" *ngIf="report">Updated {{ report.asOf | date:'shortTime' }}</span>
        </div>

        <div class="loading-state" *ngIf="loading && !report">
          <span class="spinner"></span> Connecting to health service…
        </div>

        <div class="empty-state" *ngIf="!loading && report?.integrations?.length === 0">
          No integration request logs were found.
        </div>

        <div class="integration-list" *ngIf="report as data">
          <article class="integration-card" *ngFor="let item of data.integrations">
            <div class="integration-main">
              <span class="status-dot" [ngClass]="statusClass(item.status)"></span>
              <div class="integration-name">
                <h3>{{ item.integration }}</h3>
                <span class="status-badge" [ngClass]="statusClass(item.status)">
                  {{ item.status }}
                </span>
              </div>
              <div class="metric">
                <span class="metric-label">FAILURE RATE</span>
                <strong>{{ item.errorRatePct === null ? '—' : (item.errorRatePct | number:'1.1-1') + '%' }}</strong>
                <div class="rate-track" aria-hidden="true">
                  <span [style.width.%]="barWidth(item.errorRatePct)"
                        [ngClass]="statusClass(item.status)"></span>
                </div>
              </div>
              <div class="metric compact">
                <span class="metric-label">REQUESTS</span>
                <strong>{{ item.requests }}</strong>
                <span class="metric-sub">{{ item.failures }} failed</span>
              </div>
              <div class="metric compact">
                <span class="metric-label">SUCCESS P95</span>
                <strong>{{ item.successP95LatencyMs === null ? '—' : (item.successP95LatencyMs | number:'1.0-0') + ' ms' }}</strong>
                <span class="metric-sub">latency</span>
              </div>
            </div>
            <div class="reason">
              <span class="reason-mark">{{ statusIcon(item.status) }}</span>
              <p>{{ item.reason }}</p>
            </div>
          </article>
        </div>
      </section>

      <footer>
        <span>BRIGHTLINE HR</span><span>Health classifications are based on the selected request window.</span>
      </footer>
    </main>
  `,
})
export class AppComponent implements OnInit {
  private readonly api = inject(HealthApiService);

  report: HealthReport | null = null;
  error = '';
  loading = false;
  windowMinutes = 60;

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loading = true;
    this.error = '';
    this.api.getReport(this.windowMinutes).subscribe({
      next: (report) => {
        this.report = report;
        this.loading = false;
      },
      error: (error: Error) => {
        this.error = error.message;
        this.loading = false;
      },
    });
  }

  attentionCount(report: HealthReport): number {
    return report.integrations.filter(
      (item) => item.status === 'DEGRADED' || item.status === 'UNHEALTHY',
    ).length;
  }

  statusClass(status: IntegrationHealth['status']): string {
    return status.toLowerCase().replaceAll(' ', '-');
  }

  statusIcon(status: IntegrationHealth['status']): string {
    switch (status) {
      case 'HEALTHY': return '✓';
      case 'UNHEALTHY': return '!';
      case 'DEGRADED': return '△';
      case 'NO DATA': return '–';
      default: return '?';
    }
  }

  overallNote(report: HealthReport): string {
    if (report.overallStatus === 'UNHEALTHY') return 'One or more systems are failing';
    if (report.overallStatus === 'DEGRADED') return 'Some systems need a closer look';
    if (report.overallStatus === 'NO DATA') return 'No recent events for one or more systems';
    if (report.overallStatus === 'INSUFFICIENT DATA') return 'Waiting for more request activity';
    return 'All monitored systems look good';
  }

  barWidth(rate: number | null): number {
    return rate === null ? 0 : Math.min(rate, 100);
  }
}
