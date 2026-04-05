import { AsyncPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, ChangeDetectorRef } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { AuthUser } from '../../core/models/auth.models';
import { AuthService } from '../../core/services/auth.service';
import { BrandTitleComponent } from '../../shared/components/brand-title/brand-title.component';
import { ClubLogoComponent } from '../../shared/components/club-logo/club-logo.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [AsyncPipe, FormsModule, BrandTitleComponent, ClubLogoComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly router = inject(Router);

  readonly user$: Observable<AuthUser | null> = this.auth.user$;

  authUser: any;
  user: any;
  phone: string = '';
  showPhoneInput: boolean = false;

  ngOnInit() {
    console.log('Dashboard loaded');

    const storedUser = localStorage.getItem('user');

    if (!storedUser) {
      console.error('No user found in localStorage');
      return;
    }

    this.authUser = JSON.parse(storedUser);

    console.log('Auth user:', this.authUser);  // DEBUG

    this.http.get(`/api/user?email=${this.authUser.email}`)
      .subscribe({
        next: (res: any) => {
          console.log('User API response:', res);  // DEBUG

          this.user = res;

          if (!this.user.phone) {
            this.showPhoneInput = true;
          }
          
          this.cdr.markForCheck();
        },
        error: (err) => {
          console.error('API failed:', err);
        }
      });
  }

  onPhoneInput(event: any) {
    const inputElement = event.target as HTMLInputElement;
    const sanitized = inputElement.value.replace(/[^0-9]/g, '');
    if (inputElement.value !== sanitized) {
      inputElement.value = sanitized;
    }
    this.phone = sanitized;
  }

  savePhone() {
    if (!this.phone || this.phone.length !== 10) {
      alert('Enter valid phone number');
      return;
    }
    const cleanedPhone = this.phone?.trim();

    // Regex: only 10 digits
    const phoneRegex = /^[0-9]{10}$/;

    if (!cleanedPhone || !phoneRegex.test(cleanedPhone)) {
    alert('Enter a valid 10-digit phone number (numbers only)');
    return;
    }

    if (!this.authUser?.email) {
      return;
    }

    this.http.post('/api/user/phone', {
      email: this.authUser.email,
      phone: this.phone
    }, { responseType: 'text' }).subscribe((res: any) => {
      this.user = {
        ...this.user,
        phone: this.phone,
      };
      alert(res);
      this.showPhoneInput = false;
      this.cdr.markForCheck();
    });
  }

  startFrame() {
    this.router.navigate(['/snooker-frame']);
  }
}
