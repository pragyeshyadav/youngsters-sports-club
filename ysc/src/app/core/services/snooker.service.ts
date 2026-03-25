import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';

/** Placeholder for future snooker table billing (Spring Boot integration later). */
export interface SnookerSession {
  id: string;
  tableId: string;
  startedAt: number;
  stoppedAt?: number;
}

@Injectable({ providedIn: 'root' })
export class SnookerService {
  /** Starts a play session for a table (stub). */
  startSession(tableId: string): Observable<SnookerSession> {
    const session: SnookerSession = {
      id: `sess_${Date.now()}`,
      tableId,
      startedAt: Date.now(),
    };
    return of(session).pipe(delay(0));
  }

  /** Ends a session (stub). */
  stopSession(sessionId: string): Observable<SnookerSession> {
    const session: SnookerSession = {
      id: sessionId,
      tableId: 'unknown',
      startedAt: 0,
      stoppedAt: Date.now(),
    };
    return of(session).pipe(delay(0));
  }

  /** Computes cost from duration in ms (stub rate — replace with env/pricing). */
  calculateCost(durationMs: number): Observable<number> {
    const hourlyRate = 800;
    const hours = durationMs / 3_600_000;
    return of(Math.round(hours * hourlyRate * 100) / 100).pipe(delay(0));
  }
}
