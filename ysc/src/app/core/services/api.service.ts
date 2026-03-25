import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { GoogleAuthApiResponse } from '../models/auth.models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl.replace(/\/$/, '');

  /**
   * Exchange Google id_token for application JWT (Spring Boot contract).
   * Body: { idToken: string }
   */
  postGoogleAuth(idToken: string): Observable<GoogleAuthApiResponse> {
    const url = `${this.baseUrl}/api/auth/google`;
    return this.http.post<GoogleAuthApiResponse>(url, { idToken });
  }
}
