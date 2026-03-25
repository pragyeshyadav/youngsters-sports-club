import { isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, distinctUntilChanged, map, shareReplay } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AUTH_SESSION_STORAGE_KEY, GOOGLE_TOKEN_STORAGE_KEY } from '../constants/storage.constants';
import { AuthSession, AuthUser, GoogleIdTokenClaims } from '../models/auth.models';
import { decodeJwtPayload } from '../utils/jwt.util';
import { AuthAccessTokenStore } from './auth-access-token.store';

/**
 * Frontend-only Google Identity Services (GIS) session.
 * No backend OAuth or token exchange — the Google ID token is the session credential.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly router = inject(Router);
  private readonly accessTokenStore = inject(AuthAccessTokenStore);

  private readonly sessionSubject = new BehaviorSubject<AuthSession | null>(this.readStoredSession());

  readonly session$: Observable<AuthSession | null> = this.sessionSubject.asObservable();

  readonly user$: Observable<AuthUser | null> = this.session$.pipe(
    map((s) => s?.user ?? null),
    distinctUntilChanged((a, b) => a?.email === b?.email),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly isAuthenticated$: Observable<boolean> = this.session$.pipe(
    map((s) => !!s),
    distinctUntilChanged(),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  constructor() {
    if (isPlatformBrowser(this.platformId)) {
      this.accessTokenStore.setAccessToken(this.sessionSubject.value?.accessToken ?? null);
    }
  }

  /** GIS OAuth Web Client ID (same as Console). */
  getGoogleClientId(): string {
    return environment.googleClientId;
  }

  isLoggedIn(): boolean {
    if (!isPlatformBrowser(this.platformId)) {
      return false;
    }
    return !!localStorage.getItem(GOOGLE_TOKEN_STORAGE_KEY);
  }

  getAccessToken(): string | null {
    return this.sessionSubject.value?.accessToken ?? null;
  }

  getSnapshot(): AuthSession | null {
    return this.sessionSubject.value;
  }

  /**
   * GIS credential callback: store ID token, build session from JWT claims, navigate to dashboard.
   * @returns false if credential missing or JWT decode fails.
   */
  loginWithGoogleCredential(credential: string): boolean {
    if (!isPlatformBrowser(this.platformId) || !credential) {
      return false;
    }

    let claims: GoogleIdTokenClaims;
    try {
      claims = decodeJwtPayload<GoogleIdTokenClaims>(credential);
    } catch {
      return false;
    }

    const session: AuthSession = {
      user: {
        id: claims.sub,
        name: claims.name,
        email: claims.email,
        profileImageUrl: claims.picture,
      },
      idToken: credential,
      accessToken: credential,
    };

    localStorage.setItem(GOOGLE_TOKEN_STORAGE_KEY, credential);
    this.persistSession(session);
    return true;
  }

  logout(): void {
    if (isPlatformBrowser(this.platformId)) {
      try {
        window.google?.accounts?.id?.disableAutoSelect();
      } catch {
        /* ignore */
      }
      localStorage.removeItem(GOOGLE_TOKEN_STORAGE_KEY);
      localStorage.removeItem(AUTH_SESSION_STORAGE_KEY);
    }
    this.accessTokenStore.setAccessToken(null);
    this.sessionSubject.next(null);
    void this.router.navigate(['/login']);
  }

  private persistSession(session: AuthSession): void {
    if (isPlatformBrowser(this.platformId)) {
      localStorage.setItem(AUTH_SESSION_STORAGE_KEY, JSON.stringify(session));
      localStorage.setItem(GOOGLE_TOKEN_STORAGE_KEY, session.idToken);
    }
    this.accessTokenStore.setAccessToken(session.accessToken);
    this.sessionSubject.next(session);
    void this.router.navigate(['/dashboard']);
  }

  private readStoredSession(): AuthSession | null {
    if (!isPlatformBrowser(this.platformId)) {
      return null;
    }

    const raw = localStorage.getItem(AUTH_SESSION_STORAGE_KEY);
    if (raw) {
      try {
        return JSON.parse(raw) as AuthSession;
      } catch {
        localStorage.removeItem(AUTH_SESSION_STORAGE_KEY);
      }
    }

    const googleToken = localStorage.getItem(GOOGLE_TOKEN_STORAGE_KEY);
    if (!googleToken) {
      return null;
    }

    try {
      const claims = decodeJwtPayload<GoogleIdTokenClaims>(googleToken);
      return {
        user: {
          id: claims.sub,
          name: claims.name,
          email: claims.email,
          profileImageUrl: claims.picture,
        },
        idToken: googleToken,
        accessToken: googleToken,
      };
    } catch {
      localStorage.removeItem(GOOGLE_TOKEN_STORAGE_KEY);
      return null;
    }
  }
}
