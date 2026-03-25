import { AsyncPipe } from '@angular/common';
import { Component, NgZone, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { BrandTitleComponent } from '../../../shared/components/brand-title/brand-title.component';
import { ClubLogoComponent } from '../../../shared/components/club-logo/club-logo.component';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [AsyncPipe, RouterLink, BrandTitleComponent, ClubLogoComponent],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly ngZone = inject(NgZone);

  readonly isAuthenticated$: Observable<boolean> = this.auth.isAuthenticated$;

  private googleRendered = false;

  ngOnInit(): void {
    if (this.auth.isLoggedIn()) {
      return;
    }
    this.tryInitGoogleButton(0);
  }

  /** GIS posts the credential outside Angular; re-enter the zone for routing and state. */
  handleCredentialResponse(response: { credential: string }): void {
    this.ngZone.run(() => this.auth.loginWithGoogleCredential(response.credential));
  }

  private tryInitGoogleButton(attempt: number): void {
    if (this.auth.isLoggedIn() || this.googleRendered) {
      return;
    }

    const host = document.getElementById('google-btn');
    const idApi = window.google?.accounts?.id;

    if (host && idApi) {
      this.googleRendered = true;
      this.ngZone.runOutsideAngular(() => {
        idApi.initialize({
          client_id: this.auth.getGoogleClientId(),
          callback: this.handleCredentialResponse.bind(this),
          cancel_on_tap_outside: true,
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
      return;
    }

    if (attempt < 120) {
      window.setTimeout(() => this.tryInitGoogleButton(attempt + 1), 50);
    } else {
      console.error('Google Sign-In: GIS script or #google-btn not ready after wait');
    }
  }
}
