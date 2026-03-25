import { Injectable } from '@angular/core';

/**
 * Holds the backend access token for HTTP interceptors without creating a DI cycle
 * (AuthService → ApiService → HttpClient → interceptor → must not inject AuthService).
 */
@Injectable({ providedIn: 'root' })
export class AuthAccessTokenStore {
  private accessToken: string | null = null;

  setAccessToken(token: string | null): void {
    this.accessToken = token;
  }

  getAccessToken(): string | null {
    return this.accessToken;
  }
}
