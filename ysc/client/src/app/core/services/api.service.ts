import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl.replace(/\/$/, '');

  /** Triggers backend login notification email; failures must not block the client UX. */
  notifyGoogleLogin(name: string, email: string): Observable<{ status: string }> {
    const url = `${this.baseUrl}/auth/google`;
    return this.http.post<{ status: string }>(url, { name, email });
  }
}
