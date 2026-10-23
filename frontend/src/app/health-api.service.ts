import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';
import { HealthReport } from './health.models';

@Injectable({ providedIn: 'root' })
export class HealthApiService {
  private readonly http = inject(HttpClient);

  getReport(windowMinutes: number): Observable<HealthReport> {
    const params = new HttpParams().set('windowMinutes', windowMinutes);
    return this.http.get<HealthReport>('/api/health', { params }).pipe(
      catchError((error: HttpErrorResponse) => {
        const message = error.error?.detail ?? error.error?.error ?? error.message;
        return throwError(() => new Error(`Could not load integration health: ${message}`));
      }),
    );
  }
}
