import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
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

@Component({
  selector: 'app-payment-settlement',
  standalone: true,
  imports: [CommonModule, FormsModule, BrandTitleComponent, ClubLogoComponent],
  templateUrl: './payment-settlement.component.html',
  styleUrl: './payment-settlement.component.scss',
})
export class PaymentSettlementComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private resizeHandler: (() => void) | null = null;

  searchText = '';
  users: SettlementUser[] = [];
  selectedUser: SettlementUser | null = null;
  frames: DueFrame[] = [];
  isMobile = false;
  isLoadingFrames = false;
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

    if (!query) {
      this.users = [];
      if (!this.selectedUser || this.selectedUser.name !== this.searchText) {
        this.selectedUser = null;
      }
      return;
    }

    if (this.selectedUser && this.selectedUser.name !== query) {
      this.selectedUser = null;
      this.frames = [];
    }

    this.http.get<SettlementUser[]>(`/api/users/search?query=${encodeURIComponent(query)}`).subscribe({
      next: (users) => {
        this.users = users;
      },
      error: (err) => {
        console.error('Failed to search users', err);
        this.users = [];
      },
    });
  }

  selectUser(user: SettlementUser): void {
    this.selectedUser = user;
    this.searchText = user.name;
    this.users = [];
    this.frames = [];
    this.totalDue = 0;
    this.showSettlementPopup = false;
    this.settleAmount = null;
    this.paymentMode = '';
  }

  getPlayerDetails(): void {
    if (!this.selectedUser) {
      return;
    }

    this.isLoadingFrames = true;
    this.http.get<DueFrame[]>(`/api/frame/user-due?userId=${this.selectedUser.id}`).subscribe({
      next: (frames) => {
        this.frames = frames;
        this.isLoadingFrames = false;
      },
      error: (err) => {
        console.error('Failed to load due frames', err);
        this.frames = [];
        this.isLoadingFrames = false;
      },
    });
  }

  openSettlementPopup(): void {
    if (!this.selectedUser) {
      return;
    }

    this.http.get<number>(`/api/frame/total-due?userId=${this.selectedUser.id}`).subscribe({
      next: (totalDue) => {
        this.totalDue = totalDue ?? 0;
        this.settleAmount = null;
        this.paymentMode = '';
        this.showSettlementPopup = true;
      },
      error: (err) => {
        console.error('Failed to load total due', err);
        alert('Unable to load total due right now');
      },
    });
  }

  canSave(): boolean {
    return !!this.settleAmount && this.settleAmount > 0 && this.settleAmount <= this.totalDue && !!this.paymentMode;
  }

  saveSettlement(): void {
    if (!this.selectedUser || !this.canSave()) {
      return;
    }

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
        this.getPlayerDetails();
        this.refreshTotalDue();
      },
      error: (err) => {
        console.error('Settlement error:', err);
        alert('Unable to settle payment right now');
      },
    });
  }

  closeSettlementPopup(): void {
    this.showSettlementPopup = false;
  }

  goBack(): void {
    void this.router.navigate(['/managers-portal']);
  }

  private updateViewportState(): void {
    this.isMobile = window.innerWidth < 768;
  }

  private refreshTotalDue(): void {
    if (!this.selectedUser) {
      this.totalDue = 0;
      return;
    }

    this.http.get<number>(`/api/frame/total-due?userId=${this.selectedUser.id}`).subscribe({
      next: (totalDue) => {
        this.totalDue = totalDue ?? 0;
      },
      error: (err) => {
        console.error('Failed to refresh total due', err);
      },
    });
  }
}
