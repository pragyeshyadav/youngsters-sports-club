import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { BrandTitleComponent } from '../../shared/components/brand-title/brand-title.component';
import { ClubLogoComponent } from '../../shared/components/club-logo/club-logo.component';

interface SettlementUser {
  id: number;
  name: string;
  email: string;
}

interface DueFrame {
  frameId: number;
  startTime: string;
  endTime: string | null;
  duration: number | null;
  amount: number | string | null;
  paymentDue: number | string | null;
  winnerName: string | null;
  looserName: string | null;
}

interface ConsumableDueRow {
  orderId: number;
  itemName: string;
  quantity: number;
  price: number | string | null;
  totalCost: number | string | null;
  createdAt: string;
}

interface PaymentSummary {
  frameDue: number | string | null;
  consumableDue: number | string | null;
  kidsDue: number | string | null;
  totalDue: number | string | null;
}

@Component({
  selector: 'app-payment-settlement',
  standalone: true,
  imports: [CommonModule, FormsModule, BrandTitleComponent, ClubLogoComponent],
  templateUrl: './payment-settlement.component.html',
  styleUrl: './payment-settlement.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaymentSettlementComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);
  private resizeHandler: (() => void) | null = null;

  searchText = '';
  users: SettlementUser[] = [];
  selectedUser: SettlementUser | null = null;
  frames: DueFrame[] = [];
  consumables: ConsumableDueRow[] = [];
  isMobile = false;
  isLoadingFrames = false;
  isLoadingUsers = false;
  isLoadingTotalDue = false;
  isSavingSettlement = false;
  frameDue = 0;
  consumableDue = 0;
  kidsDue = 0;
  totalDue = 0;
  showSettlementPopup = false;
  settleAmount: number | null = null;
  paymentMode = '';

  ngOnInit(): void {
    this.updateViewportState();
    this.resizeHandler = () => this.updateViewportState();
    window.addEventListener('resize', this.resizeHandler);
  }

  ngOnDestroy(): void {
    if (this.resizeHandler) {
      window.removeEventListener('resize', this.resizeHandler);
      this.resizeHandler = null;
    }
  }

  searchUsers(): void {
    const query = this.searchText.trim();

    if (query.length < 3) {
      this.users = [];
      if (!this.selectedUser || this.selectedUser.name !== this.searchText) {
        this.selectedUser = null;
      }
      this.isLoadingUsers = false;
      return;
    }

    if (this.selectedUser && this.selectedUser.name !== query) {
      this.selectedUser = null;
      this.frames = [];
      this.consumables = [];
    }

    this.isLoadingUsers = true;
    this.http.get<SettlementUser[]>(`/api/users/search?query=${encodeURIComponent(query)}`).subscribe({
      next: (users) => {
        this.users = users;
        this.isLoadingUsers = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to search users', err);
        this.users = [];
        this.isLoadingUsers = false;
        this.cdr.markForCheck();
      },
    });
  }

  selectUser(user: SettlementUser): void {
    this.selectedUser = user;
    this.searchText = user.name;
    this.users = [];
    this.frames = [];
    this.consumables = [];
    this.frameDue = 0;
    this.consumableDue = 0;
    this.totalDue = 0;
    this.showSettlementPopup = false;
    this.settleAmount = null;
    this.paymentMode = '';
    this.cdr.markForCheck();
  }

  getPlayerDetails(): void {
    if (!this.selectedUser) {
      return;
    }

    this.isLoadingFrames = true;
    this.http.get<PaymentSummary>(`/api/user/payment-summary?userId=${this.selectedUser.id}`).subscribe({
      next: (summary) => {
        this.frameDue = this.toNumber(summary?.frameDue);
        this.consumableDue = this.toNumber(summary?.consumableDue);
        this.kidsDue = this.toNumber(summary?.kidsDue);
        this.totalDue = this.toNumber(summary?.totalDue);
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load payment summary', err);
        this.frameDue = 0;
        this.consumableDue = 0;
        this.kidsDue = 0;
        this.totalDue = 0;
        this.cdr.markForCheck();
      },
    });

    forkJoin({
      frames: this.http.get<DueFrame[]>(`/api/frame/user-due?userId=${this.selectedUser.id}`),
      consumables: this.http.get<ConsumableDueRow[]>(`/api/consumables/orders/due?userId=${this.selectedUser.id}`),
    }).subscribe({
      next: ({ frames, consumables }) => {
        this.frames = frames;
        this.consumables = consumables;
        this.isLoadingFrames = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load due details', err);
        this.frames = [];
        this.consumables = [];
        this.isLoadingFrames = false;
        this.cdr.markForCheck();
      },
    });
  }

  openSettlementPopup(): void {
    if (!this.selectedUser) {
      return;
    }

    this.isLoadingTotalDue = true;
    this.http.get<PaymentSummary>(`/api/user/payment-summary?userId=${this.selectedUser.id}`).subscribe({
      next: (summary) => {
        this.frameDue = this.toNumber(summary?.frameDue);
        this.consumableDue = this.toNumber(summary?.consumableDue);
        this.kidsDue = this.toNumber(summary?.kidsDue);
        this.totalDue = this.toNumber(summary?.totalDue);
        this.settleAmount = null;
        this.paymentMode = '';
        this.showSettlementPopup = true;
        this.isLoadingTotalDue = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load total due', err);
        this.isLoadingTotalDue = false;
        this.cdr.markForCheck();
        alert('Unable to load total due right now');
      },
    });
  }

  canSave(): boolean {
    return !!this.settleAmount
      && this.settleAmount > 0
      && this.settleAmount <= this.totalDue
      && !!this.paymentMode
      && !this.isSavingSettlement;
  }

  saveSettlement(): void {
    if (!this.selectedUser || !this.canSave()) {
      return;
    }

    this.isSavingSettlement = true;
    this.http.post('/api/payment/settle', {
      userId: this.selectedUser.id,
      amount: this.settleAmount,
      mode: this.paymentMode,
    }, { responseType: 'text' }).subscribe({
      next: (res) => {
        console.log('Settlement response:', res);
        alert('Payment Settled Successfully');
        this.showSettlementPopup = false;
        this.settleAmount = null;
        this.paymentMode = '';
        this.isSavingSettlement = false;
        this.getPlayerDetails();
        this.refreshTotalDue();
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Settlement error:', err);
        this.isSavingSettlement = false;
        alert('Unable to settle payment right now');
      },
    });
  }

  closeSettlementPopup(): void {
    this.showSettlementPopup = false;
    this.cdr.markForCheck();
  }

  goBack(): void {
    void this.router.navigate(['/managers-portal']);
  }

  private updateViewportState(): void {
    this.isMobile = window.innerWidth < 768;
    this.cdr.markForCheck();
  }

  private refreshTotalDue(): void {
    if (!this.selectedUser) {
      this.frameDue = 0;
      this.consumableDue = 0;
      this.kidsDue = 0;
      this.totalDue = 0;
      return;
    }

    this.http.get<PaymentSummary>(`/api/user/payment-summary?userId=${this.selectedUser.id}`).subscribe({
      next: (summary) => {
        this.frameDue = this.toNumber(summary?.frameDue);
        this.consumableDue = this.toNumber(summary?.consumableDue);
        this.kidsDue = this.toNumber(summary?.kidsDue);
        this.totalDue = this.toNumber(summary?.totalDue);
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to refresh total due', err);
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
