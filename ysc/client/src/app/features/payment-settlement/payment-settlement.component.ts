import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subscription, forkJoin } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { OrganizationContextService } from '../../core/services/organization-context.service';
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
  private readonly authService = inject(AuthService);
  private readonly organizationContextService = inject(OrganizationContextService);
  private resizeHandler: (() => void) | null = null;
  private readonly subscriptions = new Subscription();
  private currentBranchId: number | null = null;
  private branchRequestVersion = 0;

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
  discountAmount: number | null = null;
  paymentMode = '';

  ngOnInit(): void {
    this.updateViewportState();
    this.resizeHandler = () => this.updateViewportState();
    window.addEventListener('resize', this.resizeHandler);
    this.subscribeToBranchChanges();
  }

  ngOnDestroy(): void {
    if (this.resizeHandler) {
      window.removeEventListener('resize', this.resizeHandler);
      this.resizeHandler = null;
    }
    this.subscriptions.unsubscribe();
  }

  searchUsers(): void {
    const query = this.searchText.trim();
    const requestVersion = this.branchRequestVersion;

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
        if (requestVersion !== this.branchRequestVersion) {
          return;
        }
        this.users = users;
        this.isLoadingUsers = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        if (requestVersion !== this.branchRequestVersion) {
          return;
        }
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
    this.discountAmount = null;
    this.paymentMode = '';
    this.cdr.markForCheck();
  }

  getPlayerDetails(): void {
    if (!this.selectedUser) {
      return;
    }

    this.isLoadingFrames = true;
    const headers = this.buildActorHeaders();
    const requestVersion = this.branchRequestVersion;
    this.http.get<PaymentSummary>(`/api/user/payment-summary/current-branch?userId=${this.selectedUser.id}`, { headers }).subscribe({
      next: (summary) => {
        if (requestVersion !== this.branchRequestVersion) {
          return;
        }
        this.frameDue = this.toNumber(summary?.frameDue);
        this.consumableDue = this.toNumber(summary?.consumableDue);
        this.kidsDue = this.toNumber(summary?.kidsDue);
        this.totalDue = this.toNumber(summary?.totalDue);
        this.cdr.markForCheck();
      },
      error: (err) => {
        if (requestVersion !== this.branchRequestVersion) {
          return;
        }
        console.error('Failed to load payment summary', err);
        this.frameDue = 0;
        this.consumableDue = 0;
        this.kidsDue = 0;
        this.totalDue = 0;
        this.cdr.markForCheck();
      },
    });

    forkJoin({
      frames: this.http.get<DueFrame[]>(`/api/frame/user-due/current-branch?userId=${this.selectedUser.id}`, { headers }),
      consumables: this.http.get<ConsumableDueRow[]>(`/api/consumables/orders/due/current-branch?userId=${this.selectedUser.id}`, { headers }),
    }).subscribe({
      next: ({ frames, consumables }) => {
        if (requestVersion !== this.branchRequestVersion) {
          return;
        }
        this.frames = frames;
        this.consumables = consumables;
        this.isLoadingFrames = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        if (requestVersion !== this.branchRequestVersion) {
          return;
        }
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
    const headers = this.buildActorHeaders();
    const requestVersion = this.branchRequestVersion;
    this.http.get<PaymentSummary>(`/api/user/payment-summary/current-branch?userId=${this.selectedUser.id}`, { headers }).subscribe({
      next: (summary) => {
        if (requestVersion !== this.branchRequestVersion) {
          return;
        }
        this.frameDue = this.toNumber(summary?.frameDue);
        this.consumableDue = this.toNumber(summary?.consumableDue);
        this.kidsDue = this.toNumber(summary?.kidsDue);
        this.totalDue = this.toNumber(summary?.totalDue);
        this.settleAmount = null;
        this.discountAmount = null;
        this.paymentMode = '';
        this.showSettlementPopup = true;
        this.isLoadingTotalDue = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        if (requestVersion !== this.branchRequestVersion) {
          return;
        }
        console.error('Failed to load total due', err);
        this.isLoadingTotalDue = false;
        this.cdr.markForCheck();
        alert('Unable to load total due right now');
      },
    });
  }

  canSave(): boolean {
    const paidAmount = this.settleAmount ?? 0;
    const discount = this.discountAmount ?? 0;

    return !!this.settleAmount
      && paidAmount > 0
      && discount >= 0
      && discount <= this.getMaxDiscount()
      && this.getEffectiveSettlement() <= this.totalDue
      && !!this.paymentMode
      && !this.isSavingSettlement;
  }

  saveSettlement(): void {
    if (!this.selectedUser || !this.canSave()) {
      return;
    }

    this.isSavingSettlement = true;
    const headers = this.buildActorHeaders();
    this.http.post('/api/payment/settle', {
      userId: this.selectedUser.id,
      amount: this.settleAmount,
      discount: this.discountAmount ?? 0,
      mode: this.paymentMode,
    }, { headers, responseType: 'text' }).subscribe({
      next: (res) => {
        console.log('Settlement response:', res);
        alert('Payment Settled Successfully');
        this.showSettlementPopup = false;
        this.settleAmount = null;
        this.discountAmount = null;
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
    this.settleAmount = null;
    this.discountAmount = null;
    this.paymentMode = '';
    this.cdr.markForCheck();
  }

  getEffectiveSettlement(): number {
    return this.toCurrencyNumber((this.settleAmount ?? 0) + (this.discountAmount ?? 0));
  }

  getRemainingDue(): number {
    return this.toCurrencyNumber(Math.max(0, this.totalDue - this.getEffectiveSettlement()));
  }

  getMaxDiscount(): number {
    return this.toCurrencyNumber(this.totalDue * 0.6);
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

    const headers = this.buildActorHeaders();
    const requestVersion = this.branchRequestVersion;
    this.http.get<PaymentSummary>(`/api/user/payment-summary/current-branch?userId=${this.selectedUser.id}`, { headers }).subscribe({
      next: (summary) => {
        if (requestVersion !== this.branchRequestVersion) {
          return;
        }
        this.frameDue = this.toNumber(summary?.frameDue);
        this.consumableDue = this.toNumber(summary?.consumableDue);
        this.kidsDue = this.toNumber(summary?.kidsDue);
        this.totalDue = this.toNumber(summary?.totalDue);
        this.cdr.markForCheck();
      },
      error: (err) => {
        if (requestVersion !== this.branchRequestVersion) {
          return;
        }
        console.error('Failed to refresh total due', err);
      },
    });
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
      }),
    );
  }

  private resetBranchScopedState(): void {
    this.branchRequestVersion++;
    this.searchText = '';
    this.users = [];
    this.selectedUser = null;
    this.frames = [];
    this.consumables = [];
    this.isLoadingFrames = false;
    this.isLoadingUsers = false;
    this.isLoadingTotalDue = false;
    this.isSavingSettlement = false;
    this.frameDue = 0;
    this.consumableDue = 0;
    this.kidsDue = 0;
    this.totalDue = 0;
    this.showSettlementPopup = false;
    this.settleAmount = null;
    this.discountAmount = null;
    this.paymentMode = '';
    this.cdr.markForCheck();
  }

  private toNumber(value: number | string | null | undefined): number {
    if (value === null || value === undefined || value === '') {
      return 0;
    }

    return typeof value === 'number' ? value : Number(value);
  }

  private toCurrencyNumber(value: number): number {
    return Number(value.toFixed(2));
  }

  private buildActorHeaders(): HttpHeaders {
    const actorEmail = this.authService.getSnapshot()?.user.email ?? this.getStoredUserEmail();
    return actorEmail
      ? new HttpHeaders({ 'X-User-Email': actorEmail.trim() })
      : new HttpHeaders();
  }

  private getStoredUserEmail(): string {
    if (typeof window === 'undefined') {
      return '';
    }
    try {
      const rawUser = window.localStorage.getItem('user');
      if (!rawUser) {
        return '';
      }
      const parsed = JSON.parse(rawUser) as { email?: string | null };
      return parsed?.email?.trim() ?? '';
    } catch {
      return '';
    }
  }
}
