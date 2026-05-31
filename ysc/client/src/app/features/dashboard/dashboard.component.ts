import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, ChangeDetectorRef } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { AuthUser } from '../../core/models/auth.models';
import { AuthService } from '../../core/services/auth.service';
import { BrandTitleComponent } from '../../shared/components/brand-title/brand-title.component';
import { ClubLogoComponent } from '../../shared/components/club-logo/club-logo.component';
import { ConsumableItemsComponent } from '../../shared/components/consumable-items/consumable-items.component';
import { AvailableTablesComponent } from './available-tables/available-tables.component';
import { TopRankersComponent } from './top-rankers/top-rankers.component';

interface PaymentSummary {
  totalDue: number | string | null;
}

interface PhoneVerificationUser {
  id: number;
  name: string;
  phone: string;
}

interface PhoneVerificationResponse {
  exists: boolean;
  user?: PhoneVerificationUser | null;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, BrandTitleComponent, ClubLogoComponent, ConsumableItemsComponent, AvailableTablesComponent, TopRankersComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardComponent implements OnInit {
  private static readonly CLUB_LATITUDE = 24.56744868663567;
  private static readonly CLUB_LONGITUDE = 80.86184495562104;
  private static readonly CLUB_RADIUS_METERS = 100;

  private readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly router = inject(Router);

  readonly user$: Observable<AuthUser | null> = this.auth.user$;

  authUser: any;
  user: any;
  phone: string = '';
  showPhoneInput: boolean = false;
  totalDue: number = 0;
  showDueSection: boolean = false;
  isAdmin: boolean = false;
  isManagerOrAdmin: boolean = false;
  buttonLabel: string = 'Start Snooker Frame';
  buttonColor: 'primary' | 'warn' = 'primary';
  hasOngoingFrame: boolean = false;
  userRole: string = '';
  ongoingFrameId: number | null = null;
  showFeedbackForm: boolean = false;
  rating: number = 0;
  feedbackText: string = '';
  isWithinClubRange: boolean = false;
  locationChecked: boolean = false;
  showPhoneMergePopup: boolean = false;
  phoneMergeCandidate: PhoneVerificationUser | null = null;
  phoneValidationMessage: string = '';
  isSavingPhone = false;

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
          this.userRole = this.user?.role ?? '';
          this.isAdmin = this.user?.role === 'ADMIN' || this.user?.role === 'SUPER_ADMIN';
          this.isManagerOrAdmin =
            this.user?.role === 'MANAGER' ||
            this.user?.role === 'ADMIN' ||
            this.user?.role === 'SUPER_ADMIN';

          if (!this.user.phone) {
            this.showPhoneInput = true;
          }

          this.http.get<PaymentSummary>(`/api/user/payment-summary?userId=${this.user.id}`)
            .subscribe({
              next: (summary) => {
                this.totalDue = typeof summary?.totalDue === 'number' ? summary.totalDue : Number(summary?.totalDue ?? 0);
                this.showDueSection = this.totalDue > 300;
                this.cdr.markForCheck();
              },
              error: (err) => {
                console.error('Failed to fetch total due:', err);
              }
            });

          if (this.userRole === 'CUSTOMER') {
            this.checkOngoingFrame();
            this.checkUserLocation();
          } else {
            this.locationChecked = true;
            this.isWithinClubRange = true;
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

    this.phoneValidationMessage = '';
    this.isSavingPhone = true;
    this.http.post<PhoneVerificationResponse>('/api/user/verify-phone', {
      phoneNumber: cleanedPhone,
    }).subscribe({
      next: (response) => {
        if (response?.exists && response.user) {
          this.phoneMergeCandidate = response.user;
          this.showPhoneMergePopup = true;
          this.isSavingPhone = false;
          this.cdr.markForCheck();
          return;
        }

        this.savePhoneNormally(cleanedPhone);
      },
      error: (err) => {
        console.error('Failed to verify phone number', err);
        this.isSavingPhone = false;
        alert(err?.error?.message || 'Unable to verify phone number right now');
        this.cdr.markForCheck();
      }
    });
  }

  closePhoneMergePopup(): void {
    this.showPhoneMergePopup = false;
    this.phoneMergeCandidate = null;
  }

  useDifferentPhoneNumber(): void {
    this.phoneValidationMessage = 'Please enter a different phone number';
    this.closePhoneMergePopup();
    this.cdr.markForCheck();
  }

  confirmPhoneMerge(): void {
    if (!this.authUser?.email || !this.phone) {
      return;
    }

    this.isSavingPhone = true;
    this.http.post<any>('/api/user/merge-account', {
      email: this.authUser.email,
      phoneNumber: this.phone.trim(),
    }).subscribe({
      next: (mergedUser) => {
        this.isSavingPhone = false;
        this.closePhoneMergePopup();
        this.applyResolvedUser(mergedUser, this.phone.trim());
        alert('Existing account updated successfully');
      },
      error: (err) => {
        console.error('Failed to merge user accounts', err);
        this.isSavingPhone = false;
        alert(err?.error?.message || 'Unable to update existing account right now');
        this.cdr.markForCheck();
      },
    });
  }

  checkOngoingFrame() {
    if (!this.user?.id) {
      return;
    }

    this.http.get(`/api/frame/user-ongoing?userId=${this.user.id}`)
      .subscribe({
        next: (res: any) => {
          const frame = res?.frame;
          if (frame?.id) {
            this.hasOngoingFrame = true;
            this.ongoingFrameId = frame.id;
            this.buttonLabel = 'End Snooker Frame';
            this.buttonColor = 'warn';
          } else {
            this.hasOngoingFrame = false;
            this.ongoingFrameId = null;
            this.buttonLabel = 'Start Snooker Frame';
            this.buttonColor = 'primary';
          }
          this.cdr.markForCheck();
        },
        error: (err) => {
          console.error('Failed to check ongoing frame:', err);
          this.hasOngoingFrame = false;
          this.ongoingFrameId = null;
          this.buttonLabel = 'Start Snooker Frame';
          this.buttonColor = 'primary';
          this.cdr.markForCheck();
        }
      });
  }

  private savePhoneNormally(cleanedPhone: string): void {
    this.http.post('/api/user/phone', {
      email: this.authUser.email,
      phone: cleanedPhone
    }, { responseType: 'text' }).subscribe({
      next: (res: any) => {
        this.isSavingPhone = false;
        this.applyResolvedUser(this.user, cleanedPhone);
        alert(res);
      },
      error: (err) => {
        console.error('Failed to save phone number', err);
        this.isSavingPhone = false;
        alert(err?.error?.message || 'Unable to save phone number right now');
        this.cdr.markForCheck();
      }
    });
  }

  private applyResolvedUser(resolvedUser: any, phoneNumber: string): void {
    this.user = {
      ...resolvedUser,
      phone: phoneNumber,
    };
    this.showPhoneInput = false;
    this.phoneValidationMessage = '';
    this.phone = '';

    const mergedStoredUser = {
      ...this.authUser,
      email: resolvedUser?.email ?? this.authUser?.email,
      name: resolvedUser?.name ?? this.authUser?.name,
      picture: resolvedUser?.profilePic ?? this.authUser?.picture,
      phone: phoneNumber,
    };
    localStorage.setItem('user', JSON.stringify(mergedStoredUser));
    this.authUser = mergedStoredUser;
    this.auth.updateSessionUser({
      email: mergedStoredUser.email,
      name: mergedStoredUser.name,
      profileImageUrl: mergedStoredUser.picture,
      phone: phoneNumber,
    });
    this.cdr.markForCheck();
  }

  onFrameAction() {
    if (this.userRole === 'CUSTOMER' && this.hasOngoingFrame && this.ongoingFrameId) {
      this.router.navigate(['/start-frame'], {
        state: { frameId: this.ongoingFrameId, source: 'dashboard' }
      });
      return;
    }

    this.router.navigate(['/snooker-frame']);
  }

  isCustomer(): boolean {
    return this.userRole === 'CUSTOMER';
  }

  isFrameActionDisabled(): boolean {
    return this.isCustomer() && (!this.locationChecked || !this.isWithinClubRange);
  }

  getFrameActionLabel(): string {
    if (this.isCustomer() && this.locationChecked && !this.isWithinClubRange) {
      return '📍 Reach club to start';
    }

    return this.buttonLabel;
  }

  checkUserLocation() {
    if (!navigator.geolocation) {
      this.locationChecked = true;
      this.isWithinClubRange = false;
      this.cdr.markForCheck();
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        const userLat = position.coords.latitude;
        const userLng = position.coords.longitude;

        const distance = this.calculateDistance(
          userLat,
          userLng,
          DashboardComponent.CLUB_LATITUDE,
          DashboardComponent.CLUB_LONGITUDE
        );

        this.isWithinClubRange = distance <= DashboardComponent.CLUB_RADIUS_METERS;
        this.locationChecked = true;
        this.cdr.markForCheck();
      },
      (error) => {
        console.error('Location access denied', error);
        this.locationChecked = true;
        this.isWithinClubRange = false;
        this.cdr.markForCheck();
      }
    );
  }

  calculateDistance(lat1: number, lon1: number, lat2: number, lon2: number): number {
    const earthRadius = 6371e3;
    const phi1 = lat1 * Math.PI / 180;
    const phi2 = lat2 * Math.PI / 180;
    const deltaPhi = (lat2 - lat1) * Math.PI / 180;
    const deltaLambda = (lon2 - lon1) * Math.PI / 180;

    const a =
      Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
      Math.cos(phi1) * Math.cos(phi2) *
      Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);

    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return earthRadius * c;
  }

  goToGameHistory() {
    this.router.navigate(['/my-game-history']);
  }

  toggleFeedback() {
    this.showFeedbackForm = !this.showFeedbackForm;
    this.cdr.markForCheck();
  }

  setRating(star: number) {
    this.rating = star;
    this.cdr.markForCheck();
  }

  submitFeedback() {
    if (!this.rating || !this.feedbackText.trim() || !this.user?.id) {
      alert('Please provide rating and feedback');
      return;
    }

    this.http.post('/api/feedback', {
      userId: this.user.id,
      feedback: this.feedbackText.trim(),
      starRating: this.rating
    }, { responseType: 'text' }).subscribe({
      next: () => {
        alert('Thank you for your feedback!');
        this.rating = 0;
        this.feedbackText = '';
        this.showFeedbackForm = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to save feedback:', err);
        alert('Unable to submit feedback right now');
      }
    });
  }

  goToAdminPage() {
    this.router.navigate(['/admin-page']);
  }

  goToManagersPortal() {
    this.router.navigate(['/managers-portal']);
  }

  goToKidsPlay() {
    this.router.navigate(['/kids-play']);
  }

  goToSummerOlympicsRegistration() {
    this.router.navigate(['/tournament-registration']);
  }
}
