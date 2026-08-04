import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { BrandTitleComponent } from '../../shared/components/brand-title/brand-title.component';
import { ClubLogoComponent } from '../../shared/components/club-logo/club-logo.component';
import { AuthService } from '../../core/services/auth.service';
import { OrganizationContextService } from '../../core/services/organization-context.service';
import { Subscription } from 'rxjs';

interface Tournament {
  id: number;
  name: string;
  eventName: string;
  registrationFee: number;
}

@Component({
  selector: 'app-summer-olympics-registration',
  standalone: true,
  imports: [CommonModule, FormsModule, BrandTitleComponent, ClubLogoComponent],
  templateUrl: './summer-olympics-registration.component.html',
  styleUrl: './summer-olympics-registration.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SummerOlympicsRegistrationComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly organizationContextService = inject(OrganizationContextService);
  private readonly subscriptions = new Subscription();
  private currentBranchId: number | null = null;
  private branchRequestVersion = 0;

  tournaments: Tournament[] = [];
  selectedTournamentIds = new Set<number>();
  isLoading = true;
  isSubmitting = false;
  authUser: any = null;

  showResultModal = false;
  registrationResult: any = null;

  ngOnInit(): void {
    this.subscriptions.add(this.auth.user$.subscribe(user => {
      if (user && user.email) {
        this.http.get(`/api/user?email=${encodeURIComponent(user.email)}`).subscribe({
          next: (res: any) => {
            this.authUser = res;
            this.subscribeToBranchChanges();
            this.fetchTournaments();
          },
          error: (err) => {
            console.error('Failed to load user', err);
            this.isLoading = false;
            this.cdr.markForCheck();
          }
        });
      } else {
        this.router.navigate(['/dashboard']);
      }
    }));
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  fetchTournaments(): void {
    const requestVersion = this.branchRequestVersion;
    this.http.get<Tournament[]>('/api/tournaments/active', { headers: this.buildActorHeaders() }).subscribe({
      next: (res) => {
        if (requestVersion !== this.branchRequestVersion) {
          return;
        }
        this.tournaments = res;
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        if (requestVersion !== this.branchRequestVersion) {
          return;
        }
        console.error('Failed to load active tournaments', err);
        this.isLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  toggleSelection(tournamentId: number): void {
    if (this.selectedTournamentIds.has(tournamentId)) {
      this.selectedTournamentIds.delete(tournamentId);
    } else {
      this.selectedTournamentIds.add(tournamentId);
    }
  }

  isSelected(tournamentId: number): boolean {
    return this.selectedTournamentIds.has(tournamentId);
  }

  goBack(): void {
    this.router.navigate(['/dashboard']);
  }

  submitRegistration(): void {
    if (this.selectedTournamentIds.size === 0) {
      alert('Please select at least one event');
      return;
    }

    if (!this.authUser || !this.authUser.id) {
      alert('User identity not found. Please log in again.');
      return;
    }

    this.isSubmitting = true;
    const payload = {
      userId: this.authUser.id,
      tournamentIds: Array.from(this.selectedTournamentIds)
    };

    this.http.post<any>('/api/tournaments/register', payload, { headers: this.buildActorHeaders() }).subscribe({
      next: (res) => {
        this.isSubmitting = false;
        this.registrationResult = res;
        this.showResultModal = true;
        this.selectedTournamentIds.clear();
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Registration failed', err);
        alert('An error occurred during registration. Please try again.');
        this.isSubmitting = false;
        this.cdr.markForCheck();
      }
    });
  }

  closeModal(): void {
    this.showResultModal = false;
    this.router.navigate(['/dashboard']);
  }

  getFeeDisplay(fee: number): string {
    if (fee == null || fee === 0) return 'Free Registration';
    return `Fee: ₹${fee}`;
  }

  getIconForTournament(name: string): string {
    const lower = name.toLowerCase();
    if (lower.includes('snooker')) return '🎱';
    if (lower.includes('pool') || lower.includes('8 ball')) return '🎱';
    if (lower.includes('tennis')) return '🏓';
    if (lower.includes('carrom')) return '🎯';
    if (lower.includes('chess')) return '♟️';
    return '🏆';
  }

  private subscribeToBranchChanges(): void {
    this.currentBranchId = this.organizationContextService.getSnapshot()?.currentBranch?.id ?? null;
    this.subscriptions.add(
      this.organizationContextService.currentBranchId$.subscribe((branchId) => {
        if (this.currentBranchId === branchId) {
          return;
        }

        this.currentBranchId = branchId;
        this.resetBranchScopedState();
        if (branchId && this.authUser?.id) {
          this.fetchTournaments();
        }
      }),
    );
  }

  private resetBranchScopedState(): void {
    this.branchRequestVersion++;
    this.tournaments = [];
    this.selectedTournamentIds.clear();
    this.showResultModal = false;
    this.registrationResult = null;
    this.isLoading = !!this.currentBranchId;
    this.isSubmitting = false;
    this.cdr.markForCheck();
  }

  private buildActorHeaders(): HttpHeaders {
    const email = this.auth.getSnapshot()?.user?.email || this.getStoredUserEmail();
    return email ? new HttpHeaders({ 'X-User-Email': email }) : new HttpHeaders();
  }

  private getStoredUserEmail(): string | null {
    try {
      const storedUser = localStorage.getItem('user');
      if (!storedUser) {
        return null;
      }
      const parsed = JSON.parse(storedUser);
      return typeof parsed?.email === 'string' ? parsed.email : null;
    } catch {
      return null;
    }
  }
}
