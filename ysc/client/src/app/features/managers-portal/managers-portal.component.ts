import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { BrandTitleComponent } from '../../shared/components/brand-title/brand-title.component';
import { ClubLogoComponent } from '../../shared/components/club-logo/club-logo.component';

interface OngoingFrame {
  id: number;
  tableId: number | null;
  tableName: string | null;
  startTime: string;
  status: string;
  startedBy: string | null;
  players: string[];
}

interface CompletedFrame {
  id: number;
  winnerName: string | null;
  looserName: string | null;
  startTime: string;
  endTime: string;
  durationMinutes: number | null;
  totalAmount: number | string | null;
  paymentDue: number | string | null;
}

interface PlayerSummary {
  userId: number;
  name: string;
  email: string;
  framesPlayed: number;
  totalDue: number;
}

interface DuePlayer {
  name: string;
  due: number | string | null;
}

interface TodayEarnings {
  totalEarnings: number | string | null;
  totalDue: number | string | null;
  duePlayers: DuePlayer[];
}

interface MessageResponse {
  message: string;
}

@Component({
  selector: 'app-managers-portal',
  standalone: true,
  imports: [CommonModule, FormsModule, BrandTitleComponent, ClubLogoComponent],
  templateUrl: './managers-portal.component.html',
  styleUrl: './managers-portal.component.scss',
})
export class ManagersPortalComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private resizeHandler: (() => void) | null = null;
  private readonly today = new Date();

  isOngoingExpanded = false;
  ongoingFrames: OngoingFrame[] = [];
  isCompletedExpanded = false;
  completedFrames: CompletedFrame[] = [];
  isMobile = false;
  isLoadingOngoing = false;
  isLoadingCompleted = false;
  selectedCompletedDate = '';
  minCompletedDate = '';
  maxCompletedDate = '';
  isEarningsExpanded = false;
  isLoadingEarnings = false;
  hasLoadedEarnings = false;
  canViewTodayEarnings = false;
  todayEarnings: TodayEarnings = {
    totalEarnings: 0,
    totalDue: 0,
    duePlayers: [],
  };
  isAddCustomerExpanded = false;
  isSavingCustomer = false;
  customerForm = {
    name: '',
    email: '',
    mobileNumber: '',
  };

  isPlayersExpanded = false;
  isLoadingPlayers = false;
  players: PlayerSummary[] = [];
  playersPage = 0;
  hasMorePlayers = true;

  ngOnInit(): void {
    this.updateViewportState();
    this.maxCompletedDate = this.formatDate(this.today);
    this.selectedCompletedDate = this.maxCompletedDate;
    const minDate = new Date(this.today);
    minDate.setDate(minDate.getDate() - 60);
    this.minCompletedDate = this.formatDate(minDate);
    this.resizeHandler = () => this.updateViewportState();
    window.addEventListener('resize', this.resizeHandler);
    this.loadViewerAccess();
  }

  ngOnDestroy(): void {
    if (this.resizeHandler) {
      window.removeEventListener('resize', this.resizeHandler);
      this.resizeHandler = null;
    }
  }

  toggleOngoing(): void {
    this.isOngoingExpanded = !this.isOngoingExpanded;

    if (this.isOngoingExpanded && this.ongoingFrames.length === 0) {
      this.loadOngoingFrames();
    }
  }

  toggleEarnings(): void {
    if (!this.canViewTodayEarnings) {
      return;
    }

    this.isEarningsExpanded = !this.isEarningsExpanded;

    if (this.isEarningsExpanded && !this.isLoadingEarnings && !this.hasLoadedEarnings) {
      this.loadTodayEarnings();
    }
  }

  loadTodayEarnings(): void {
    this.isLoadingEarnings = true;

    this.http.get<TodayEarnings>('/api/analytics/today-earnings').subscribe({
      next: (earnings) => {
        this.todayEarnings = {
          totalEarnings: earnings?.totalEarnings ?? 0,
          totalDue: earnings?.totalDue ?? 0,
          duePlayers: earnings?.duePlayers ?? [],
        };
        this.hasLoadedEarnings = true;
        this.isLoadingEarnings = false;
      },
      error: (err) => {
        console.error('Failed to load today earnings', err);
        this.todayEarnings = {
          totalEarnings: 0,
          totalDue: 0,
          duePlayers: [],
        };
        this.hasLoadedEarnings = false;
        this.isLoadingEarnings = false;
      },
    });
  }

  loadOngoingFrames(): void {
    this.isLoadingOngoing = true;

    this.http.get<OngoingFrame[]>('/api/frame/ongoing/today').subscribe({
      next: (frames) => {
        this.ongoingFrames = frames;
        this.isLoadingOngoing = false;
      },
      error: (err) => {
        console.error('Failed to load ongoing frames', err);
        this.ongoingFrames = [];
        this.isLoadingOngoing = false;
      },
    });
  }

  toggleCompleted(): void {
    this.isCompletedExpanded = !this.isCompletedExpanded;

    if (this.isCompletedExpanded && this.completedFrames.length === 0) {
      this.loadCompletedFrames(true);
    }
  }

  loadCompletedFrames(useTodayApi: boolean = false): void {
    this.isLoadingCompleted = true;

    const request$ = useTodayApi
      ? this.http.get<CompletedFrame[]>('/api/frame/completed/today')
      : this.http.get<CompletedFrame[]>(`/api/frame/completed?date=${this.selectedCompletedDate}`);

    request$.subscribe({
      next: (frames) => {
        this.completedFrames = frames;
        this.isLoadingCompleted = false;
      },
      error: (err) => {
        console.error('Failed to load completed frames', err);
        this.completedFrames = [];
        this.isLoadingCompleted = false;
      },
    });
  }

  onCompletedDateChange(): void {
    if (!this.selectedCompletedDate) {
      return;
    }

    if (this.selectedCompletedDate < this.minCompletedDate || this.selectedCompletedDate > this.maxCompletedDate) {
      alert('Please select a valid date within the last 60 days');
      this.selectedCompletedDate = this.maxCompletedDate;
      return;
    }

    if (!this.isCompletedExpanded) {
      this.isCompletedExpanded = true;
    }

    if (this.selectedCompletedDate === this.maxCompletedDate) {
      this.loadCompletedFrames(true);
      return;
    }

    this.loadCompletedFrames();
  }

  toggleAddCustomer(): void {
    this.isAddCustomerExpanded = !this.isAddCustomerExpanded;
  }

  onCustomerMobileInput(event: Event): void {
    const inputElement = event.target as HTMLInputElement;
    const sanitized = inputElement.value.replace(/[^0-9]/g, '').slice(0, 10);
    if (inputElement.value !== sanitized) {
      inputElement.value = sanitized;
    }
    this.customerForm.mobileNumber = sanitized;
  }

  isCustomerFormValid(): boolean {
    return this.customerForm.name.trim().length > 0
      && this.isValidEmail(this.customerForm.email)
      && /^[0-9]{10}$/.test(this.customerForm.mobileNumber)
      && !this.isSavingCustomer;
  }

  hasCustomerFormMissingFields(): boolean {
    return this.customerForm.name.trim().length === 0
      || this.customerForm.email.trim().length === 0
      || this.customerForm.mobileNumber.trim().length === 0;
  }

  hasCustomerEmailError(): boolean {
    return !this.hasCustomerFormMissingFields() && !this.isValidEmail(this.customerForm.email);
  }

  hasCustomerMobileError(): boolean {
    return !this.hasCustomerFormMissingFields() && !/^[0-9]{10}$/.test(this.customerForm.mobileNumber);
  }

  saveCustomer(): void {
    if (!this.isCustomerFormValid()) {
      return;
    }

    this.isSavingCustomer = true;
    this.http.post<MessageResponse>('/api/users/create-customer', {
      name: this.customerForm.name.trim(),
      email: this.customerForm.email.trim().toLowerCase(),
      mobileNumber: this.customerForm.mobileNumber.trim(),
    }).subscribe({
      next: (response) => {
        this.isSavingCustomer = false;
        alert(response?.message || 'Customer added successfully');
        this.resetCustomerForm();
        this.isAddCustomerExpanded = false;
      },
      error: (err) => {
        console.error('Failed to create customer', err);
        this.isSavingCustomer = false;
        alert(err?.error?.message || 'Unable to add customer right now');
      },
    });
  }

  togglePlayers(): void {
    this.isPlayersExpanded = !this.isPlayersExpanded;

    if (this.isPlayersExpanded && this.players.length === 0) {
      this.loadPlayers();
    }
  }

  loadPlayers(): void {
    if (this.isLoadingPlayers || !this.hasMorePlayers) return;
    this.isLoadingPlayers = true;

    this.http.get<any>(`/api/users/player-summary?page=${this.playersPage}&size=20`).subscribe({
      next: (response) => {
        const content = response.content || [];
        this.players = [...this.players, ...content];
        this.isLoadingPlayers = false;

        if (content.length < 20 || response.last) {
          this.hasMorePlayers = false;
        } else {
          this.playersPage++;
        }
      },
      error: (err) => {
        console.error('Failed to load players', err);
        this.isLoadingPlayers = false;
      },
    });
  }

  onPlayersScroll(event: Event): void {
    if (!this.isPlayersExpanded) return;
    const target = event.target as HTMLElement;
    if (target.scrollHeight - target.scrollTop <= target.clientHeight + 100) {
      this.loadPlayers();
    }
  }

  endFrame(frameId: number): void {
    void this.router.navigate(['/start-frame'], { state: { frameId, source: 'manager-portal' } });
  }

  rejectFrame(frameId: number): void {
    this.http.post(`/api/frame/reject/${frameId}`, {}).subscribe({
      next: () => {
        this.loadOngoingFrames();
      },
      error: (err) => {
        console.error('Failed to reject frame', err);
        alert('Unable to reject frame right now');
      },
    });
  }

  goToSettlement(): void {
    void this.router.navigate(['/payment-settlement']);
  }

  getPaymentRowClass(frame: CompletedFrame): string {
    const paymentDue = this.toNumber(frame.paymentDue);
    const totalAmount = this.toNumber(frame.totalAmount);

    if (paymentDue <= 0) {
      return 'paid-row';
    }

    if (totalAmount > 0 && paymentDue > 0 && paymentDue < totalAmount) {
      return 'partial-row';
    }

    return 'due-row';
  }

  private updateViewportState(): void {
    this.isMobile = window.innerWidth < 768;
  }

  private loadViewerAccess(): void {
    const storedUser = localStorage.getItem('user');
    if (!storedUser) {
      this.canViewTodayEarnings = false;
      return;
    }

    try {
      const authUser = JSON.parse(storedUser) as { email?: string };
      if (!authUser.email) {
        this.canViewTodayEarnings = false;
        return;
      }

      this.http.get<{ role?: string }>(`/api/user?email=${encodeURIComponent(authUser.email)}`).subscribe({
        next: (user) => {
          this.canViewTodayEarnings = ['MANAGER', 'ADMIN', 'SUPER_ADMIN'].includes(user?.role ?? '');
        },
        error: (err) => {
          console.error('Failed to load viewer role', err);
          this.canViewTodayEarnings = false;
        },
      });
    } catch (error) {
      console.error('Failed to parse stored user', error);
      this.canViewTodayEarnings = false;
    }
  }

  private toNumber(value: number | string | null): number {
    if (value === null || value === undefined || value === '') {
      return 0;
    }

    return typeof value === 'number' ? value : Number(value);
  }

  private formatDate(date: Date): string {
    const year = date.getFullYear();
    const month = `${date.getMonth() + 1}`.padStart(2, '0');
    const day = `${date.getDate()}`.padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private isValidEmail(email: string): boolean {
    const normalizedEmail = email == null ? '' : email.trim();
    return /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$/.test(normalizedEmail);
  }

  private resetCustomerForm(): void {
    this.customerForm = {
      name: '',
      email: '',
      mobileNumber: '',
    };
  }
}
