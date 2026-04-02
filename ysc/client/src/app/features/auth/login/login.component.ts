/**
 * Google Identity Services (GIS). client_id must match a valid Web OAuth client in Google Cloud.
 * Console → Authorized JavaScript origins must include window.location.origin (e.g. http://localhost:8080).
 */
import { AsyncPipe, isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import {
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  NgZone,
  PLATFORM_ID,
  inject,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import type { GoogleCredentialResponse } from '../../../../types/google-identity';
import { AuthService } from '../../../core/services/auth.service';
import { BrandTitleComponent } from '../../../shared/components/brand-title/brand-title.component';
import { ClubLogoComponent } from '../../../shared/components/club-logo/club-logo.component';

function isConfiguredGoogleClientId(id: string): boolean {
  if (!id?.trim()) {
    return false;
  }
  const t = id.trim();
  if (t.length < 30) {
    return false;
  }
  if (/YOUR_|PLACEHOLDER|CHANGE_ME|example\.com/i.test(t)) {
    return false;
  }
  return t.endsWith('.apps.googleusercontent.com');
}

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [AsyncPipe, RouterLink, BrandTitleComponent, ClubLogoComponent],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent implements AfterViewInit {
  private readonly auth = inject(AuthService);
  private readonly ngZone = inject(NgZone);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly isAuthenticated$: Observable<boolean> = this.auth.isAuthenticated$;

  googleInitFailed = false;
  googleLoginError = false;
  /** Missing/placeholder client ID in environment. */
  googleClientConfigInvalid = false;

  private googleRendered = false;

  ngAfterViewInit(): void {
    if (!isPlatformBrowser(this.platformId) || this.auth.isLoggedIn()) {
      return;
    }

    console.log('Origin:', window.location.origin);
    console.log('Client ID:', environment.googleClientId);
    console.log('Using Client ID:', environment.googleClientId);

    if (!isConfiguredGoogleClientId(environment.googleClientId)) {
      console.error('Google Sign-In: invalid or placeholder googleClientId in environment');
      this.googleClientConfigInvalid = true;
      this.cdr.markForCheck();
      return;
    }

    const start = (): void => {
      window.setTimeout(() => this.tryInitGoogleButton(0), 0);
    };

    if (document.readyState === 'complete') {
      start();
    } else {
      window.addEventListener('load', start, { once: true });
    }
  }

  parseJwt(token: string): any {
    try {
      return JSON.parse(atob(token.split('.')[1]));
    } catch (e) {
      return null;
    }
  }

  handleCredentialResponse(response: any): void {
    this.ngZone.run(() => {
      if (!response?.credential) {
        this.googleLoginError = true;
        this.cdr.markForCheck();
        return;
      }
      
      const user = this.parseJwt(response.credential);
      if (!user) {
        this.googleLoginError = true;
        this.cdr.markForCheck();
        return;
      }

      const payload = {
        name: user.name,
        email: user.email,
        googleId: user.sub,
        profilePic: user.picture
      };

      this.http.post('/api/auth/google-login', payload, { responseType: 'text' })
        .subscribe(() => {
          this.ngZone.run(() => {
            this.auth.loginWithGoogleCredential(response.credential);
            this.router.navigate(['/dashboard']).then(success => {
              if (!success) {
                console.error('Navigation to dashboard failed!');
                window.location.href = '/dashboard';
              }
            });
          });
        });
    });
  }

  private tryInitGoogleButton(attempt: number): void {
    if (
      !isPlatformBrowser(this.platformId) ||
      this.auth.isLoggedIn() ||
      this.googleRendered ||
      this.googleClientConfigInvalid
    ) {
      return;
    }

    const host = document.getElementById('google-btn');
    const idApi = window.google?.accounts?.id;

    if (host && idApi) {
      try {
        this.googleRendered = true;
        this.ngZone.runOutsideAngular(() => {
          idApi.initialize({
            client_id: environment.googleClientId,
            callback: this.handleCredentialResponse.bind(this),
            cancel_on_tap_outside: true,
            use_fedcm_for_prompt: false,
          });
          host.innerHTML = '';
          idApi.renderButton(host, {
            theme: 'outline',
            size: 'large',
            width: 320,
            text: 'signin_with',
            shape: 'rectangular',
          });
        });
      } catch (e) {
        console.error('Google Sign-In initialize/render failed', e);
        this.googleRendered = false;
        this.googleInitFailed = true;
        this.cdr.markForCheck();
      }
      return;
    }

    if (attempt < 120) {
      window.setTimeout(() => this.tryInitGoogleButton(attempt + 1), 50);
    } else {
      console.error('Google Sign-In: GIS script or #google-btn not ready after wait');
      this.googleInitFailed = true;
      this.cdr.markForCheck();
    }
  }
}
