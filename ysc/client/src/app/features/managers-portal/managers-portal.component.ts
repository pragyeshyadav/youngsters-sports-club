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

interface SettlementUser {
  id: number;
  name: string;
  email: string;
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

interface ConsumableItemOption {
  id: number;
  name: string;
  price: number | string | null;
}

interface SelectedConsumableItem {
  itemId: number;
  name: string;
  price: number;
  quantity: number;
  totalCost: number;
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

  isOngoingExpanded = false;
  ongoingFrames: OngoingFrame[] = [];
  isCompletedExpanded = false;
  completedFrames: CompletedFrame[] = [];
  isMobile = false;
  isLoadingOngoing = false;
  isLoadingCompleted = false;
  isEarningsExpanded = false;
  isLoadingEarnings = false;
  hasLoadedEarnings = false;
  canViewTodayEarnings = false;
  todayEarnings: TodayEarnings = {
    totalEarnings: 0,
    totalDue: 0,
    duePlayers: [],
  };
  isConsumablesExpanded = false;
  consumableUserSearchText = '';
  consumableUsers: SettlementUser[] = [];
  selectedConsumableUser: SettlementUser | null = null;
  consumableItemSearchText = '';
  consumableItems: ConsumableItemOption[] = [];
  selectedConsumableItem: ConsumableItemOption | null = null;
  selectedConsumableQuantity = 1;
  selectedConsumableOrderItems: SelectedConsumableItem[] = [];
  isLoadingConsumableUsers = false;
  isLoadingConsumableItems = false;
  isSubmittingConsumableOrder = false;

  isPlayersExpanded = false;
  isLoadingPlayers = false;
  players: PlayerSummary[] = [];
  playersPage = 0;
  hasMorePlayers = true;

  ngOnInit(): void {
    this.updateViewportState();
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
      this.loadCompletedFrames();
    }
  }

  loadCompletedFrames(): void {
    this.isLoadingCompleted = true;

    this.http.get<CompletedFrame[]>('/api/frame/completed/today').subscribe({
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

  toggleConsumables(): void {
    this.isConsumablesExpanded = !this.isConsumablesExpanded;
  }

  searchConsumableUsers(): void {
    const query = this.consumableUserSearchText.trim();

    if (query.length < 3) {
      this.consumableUsers = [];
      this.isLoadingConsumableUsers = false;
      if (!this.selectedConsumableUser || this.selectedConsumableUser.name !== this.consumableUserSearchText) {
        this.selectedConsumableUser = null;
        this.selectedConsumableOrderItems = [];
      }
      return;
    }

    if (this.selectedConsumableUser && this.selectedConsumableUser.name !== query) {
      this.selectedConsumableUser = null;
      this.selectedConsumableOrderItems = [];
    }

    this.isLoadingConsumableUsers = true;
    this.http.get<SettlementUser[]>(`/api/users/search?query=${encodeURIComponent(query)}`).subscribe({
      next: (users) => {
        this.consumableUsers = users;
        this.isLoadingConsumableUsers = false;
      },
      error: (err) => {
        console.error('Failed to search consumable users', err);
        this.consumableUsers = [];
        this.isLoadingConsumableUsers = false;
      },
    });
  }

  selectConsumableUser(user: SettlementUser): void {
    this.selectedConsumableUser = user;
    this.consumableUserSearchText = user.name;
    this.consumableUsers = [];
    this.selectedConsumableOrderItems = [];
    this.selectedConsumableItem = null;
    this.consumableItemSearchText = '';
    this.consumableItems = [];
    this.selectedConsumableQuantity = 1;
  }

  searchConsumableItems(): void {
    const query = this.consumableItemSearchText.trim();

    if (query.length < 3) {
      this.consumableItems = [];
      this.isLoadingConsumableItems = false;
      if (!this.selectedConsumableItem || this.selectedConsumableItem.name !== this.consumableItemSearchText) {
        this.selectedConsumableItem = null;
      }
      return;
    }

    if (this.selectedConsumableItem && this.selectedConsumableItem.name !== query) {
      this.selectedConsumableItem = null;
    }

    this.isLoadingConsumableItems = true;
    this.http.get<ConsumableItemOption[]>(`/api/consumables/items/search?query=${encodeURIComponent(query)}`).subscribe({
      next: (items) => {
        this.consumableItems = items;
        this.isLoadingConsumableItems = false;
      },
      error: (err) => {
        console.error('Failed to search consumable items', err);
        this.consumableItems = [];
        this.isLoadingConsumableItems = false;
      },
    });
  }

  selectConsumableItem(item: ConsumableItemOption): void {
    this.selectedConsumableItem = item;
    this.consumableItemSearchText = item.name;
    this.consumableItems = [];
  }

  addConsumableItem(): void {
    if (!this.selectedConsumableUser || !this.selectedConsumableItem) {
      return;
    }

    const price = this.toNumber(this.selectedConsumableItem.price);
    const existingIndex = this.selectedConsumableOrderItems.findIndex(
      (selected) => selected.itemId === this.selectedConsumableItem?.id,
    );

    if (existingIndex >= 0) {
      const updated = [...this.selectedConsumableOrderItems];
      const existing = updated[existingIndex];
      existing.quantity += this.selectedConsumableQuantity;
      existing.totalCost = existing.price * existing.quantity;
      this.selectedConsumableOrderItems = updated;
    } else {
      this.selectedConsumableOrderItems = [
        ...this.selectedConsumableOrderItems,
        {
          itemId: this.selectedConsumableItem.id,
          name: this.selectedConsumableItem.name,
          price,
          quantity: this.selectedConsumableQuantity,
          totalCost: price * this.selectedConsumableQuantity,
        },
      ];
    }

    this.selectedConsumableItem = null;
    this.consumableItemSearchText = '';
    this.consumableItems = [];
    this.selectedConsumableQuantity = 1;
  }

  removeConsumableItem(itemId: number): void {
    this.selectedConsumableOrderItems = this.selectedConsumableOrderItems.filter((item) => item.itemId !== itemId);
  }

  getConsumableOrderTotal(): number {
    return this.selectedConsumableOrderItems.reduce((sum, item) => sum + item.totalCost, 0);
  }

  submitConsumableOrder(): void {
    if (!this.selectedConsumableUser || this.selectedConsumableOrderItems.length === 0 || this.isSubmittingConsumableOrder) {
      return;
    }

    this.isSubmittingConsumableOrder = true;
    this.http.post<{ orderId: number; totalAmount: number }>('/api/consumables/order', {
      userId: this.selectedConsumableUser.id,
      items: this.selectedConsumableOrderItems.map((item) => ({
        itemId: item.itemId,
        quantity: item.quantity,
      })),
    }).subscribe({
      next: () => {
        alert('Consumable order saved successfully');
        this.selectedConsumableOrderItems = [];
        this.selectedConsumableItem = null;
        this.consumableItemSearchText = '';
        this.consumableItems = [];
        this.selectedConsumableQuantity = 1;
        this.selectedConsumableUser = null;
        this.consumableUserSearchText = '';
        this.consumableUsers = [];
        this.isSubmittingConsumableOrder = false;
      },
      error: (err) => {
        console.error('Failed to submit consumable order', err);
        this.isSubmittingConsumableOrder = false;
        alert('Unable to save consumable order right now');
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
}
