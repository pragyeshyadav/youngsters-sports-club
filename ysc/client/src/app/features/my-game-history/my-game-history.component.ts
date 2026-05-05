import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { BrandTitleComponent } from '../../shared/components/brand-title/brand-title.component';
import { ClubLogoComponent } from '../../shared/components/club-logo/club-logo.component';
import { AuthService } from '../../core/services/auth.service';

interface BackendUser {
  id: number;
  email: string;
}

interface GameHistoryRow {
  frameId: number;
  winnerName?: string | null;
  looserName?: string | null;
  startTime: string;
  endTime?: string | null;
  duration?: number | null;
  amount?: number | null;
  paymentDue?: number | null;
}

interface PaymentSummary {
  frameDue: number | string | null;
  consumableDue: number | string | null;
  kidsDue: number | string | null;
  totalDue: number | string | null;
}

interface ConsumableHistoryRow {
  itemName: string;
  quantity: number;
  date: string;
  amount: number | string | null;
  paymentStatus: string;
}

@Component({
  selector: 'app-my-game-history',
  standalone: true,
  imports: [CommonModule, BrandTitleComponent, ClubLogoComponent],
  templateUrl: './my-game-history.component.html',
  styleUrl: './my-game-history.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MyGameHistoryComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly handleResize = () => {
    this.isMobile = window.innerWidth < 768;
    this.cdr.markForCheck();
  };

  history: GameHistoryRow[] = [];
  consumableHistory: ConsumableHistoryRow[] = [];
  isMobile = false;
  frameDue = 0;
  consumableDue = 0;
  kidsDue = 0;
  totalDue = 0;
  isLoadingHistory = false;
  isLoadingTotalDue = false;
  isConsumableHistoryExpanded = false;
  isLoadingConsumableHistory = false;
  hasLoadedConsumableHistory = false;
  currentUserId: number | null = null;

  ngOnInit(): void {
    this.isMobile = window.innerWidth < 768;
    window.addEventListener('resize', this.handleResize);

    const email = this.auth.getSnapshot()?.user.email;
    if (!email) {
      return;
    }

    this.http.get<BackendUser>(`/api/user?email=${encodeURIComponent(email)}`).subscribe({
      next: (user) => {
        this.currentUserId = user.id;
        this.isLoadingTotalDue = true;
        this.http.get<PaymentSummary>(`/api/user/payment-summary?userId=${user.id}`).subscribe({
          next: (res) => {
            this.frameDue = this.toNumber(res?.frameDue);
            this.consumableDue = this.toNumber(res?.consumableDue);
            this.kidsDue = this.toNumber(res?.kidsDue);
            this.totalDue = this.toNumber(res?.totalDue);
            this.isLoadingTotalDue = false;
            this.cdr.markForCheck();
          },
          error: (err) => {
            console.error('Failed to load total due', err);
            this.frameDue = 0;
            this.consumableDue = 0;
            this.kidsDue = 0;
            this.totalDue = 0;
            this.isLoadingTotalDue = false;
            this.cdr.markForCheck();
          },
        });

        this.isLoadingHistory = true;
        this.http.get<GameHistoryRow[]>(`/api/frame/history?userId=${user.id}`).subscribe({
          next: (res) => {
            this.history = res;
            this.isLoadingHistory = false;
            this.cdr.markForCheck();
          },
          error: (err) => {
            console.error('Failed to load game history', err);
            this.history = [];
            this.isLoadingHistory = false;
            this.cdr.markForCheck();
          },
        });
      },
      error: (err) => {
        console.error('Failed to load current user', err);
        this.isLoadingHistory = false;
        this.isLoadingTotalDue = false;
        this.cdr.markForCheck();
      },
    });
  }

  ngOnDestroy(): void {
    window.removeEventListener('resize', this.handleResize);
  }

  getRowClass(frame: GameHistoryRow): string {
    if (!frame.endTime) {
      return 'running-row';
    }
    if ((frame.paymentDue ?? 0) > 0) {
      return 'due-row';
    }
    return '';
  }

  toggleConsumableHistory(): void {
    this.isConsumableHistoryExpanded = !this.isConsumableHistoryExpanded;

    if (this.isConsumableHistoryExpanded && !this.hasLoadedConsumableHistory) {
      this.loadConsumableHistory();
    }
  }

  getConsumableRowClass(row: ConsumableHistoryRow): string {
    const status = (row.paymentStatus || '').toUpperCase();
    if (status === 'PAID') {
      return 'paid-row';
    }
    if (status === 'PARTIAL') {
      return 'partial-row';
    }
    return 'due-row';
  }

  private loadConsumableHistory(): void {
    if (!this.currentUserId || this.isLoadingConsumableHistory) {
      return;
    }

    this.isLoadingConsumableHistory = true;
    this.http.get<ConsumableHistoryRow[]>(`/api/consumables/my-history?userId=${this.currentUserId}`).subscribe({
      next: (rows) => {
        this.consumableHistory = rows ?? [];
        this.isLoadingConsumableHistory = false;
        this.hasLoadedConsumableHistory = true;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load consumable history', err);
        this.consumableHistory = [];
        this.isLoadingConsumableHistory = false;
        this.hasLoadedConsumableHistory = true;
        this.cdr.markForCheck();
      },
    });
  }

  private toNumber(value: number | string | null | undefined): number {
    if (value === null || value === undefined || value === '') {
      return 0;
    }

    return typeof value === 'number' ? value : Number(value);
  }
}
