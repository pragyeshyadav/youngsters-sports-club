import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription, finalize } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { OrganizationContextService } from '../../../core/services/organization-context.service';

interface SettlementUser {
  id: number;
  name: string;
  email: string;
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
  selector: 'app-consumable-items',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './consumable-items.component.html',
  styleUrl: './consumable-items.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConsumableItemsComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly authService = inject(AuthService);
  private readonly organizationContextService = inject(OrganizationContextService);
  private readonly subscriptions = new Subscription();
  private userSearchRequestId = 0;
  private itemSearchRequestId = 0;
  private currentBranchId: number | null = null;

  isExpanded = false;
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

  ngOnInit(): void {
    this.currentBranchId = this.organizationContextService.getSnapshot()?.currentBranch?.id ?? null;
    this.subscriptions.add(
      this.organizationContextService.currentBranchId$.subscribe((branchId) => {
        if (this.currentBranchId === branchId) {
          return;
        }

        this.currentBranchId = branchId;
        this.resetStateForBranchChange();
      }),
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  toggle(): void {
    this.isExpanded = !this.isExpanded;
    this.cdr.markForCheck();
  }

  searchConsumableUsers(): void {
    const query = this.consumableUserSearchText.trim();
    const requestId = ++this.userSearchRequestId;

    if (query.length < 3) {
      this.consumableUsers = [];
      this.isLoadingConsumableUsers = false;
      if (!this.selectedConsumableUser || this.selectedConsumableUser.name !== this.consumableUserSearchText) {
        this.selectedConsumableUser = null;
        this.selectedConsumableOrderItems = [];
      }
      this.cdr.markForCheck();
      return;
    }

    if (this.selectedConsumableUser && this.selectedConsumableUser.name !== query) {
      this.selectedConsumableUser = null;
      this.selectedConsumableOrderItems = [];
    }

    this.isLoadingConsumableUsers = true;
    this.cdr.markForCheck();
    this.http.get<SettlementUser[]>(
      `/api/users/search/current-branch?query=${encodeURIComponent(query)}`,
      { headers: this.buildActorHeaders() },
    ).subscribe({
      next: (users) => {
        if (requestId !== this.userSearchRequestId) {
          return;
        }
        this.consumableUsers = users;
        this.isLoadingConsumableUsers = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        if (requestId !== this.userSearchRequestId) {
          return;
        }
        console.error('Failed to search consumable users', err);
        this.consumableUsers = [];
        this.isLoadingConsumableUsers = false;
        this.cdr.markForCheck();
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
    this.isLoadingConsumableItems = false;
    this.itemSearchRequestId++;
    this.cdr.markForCheck();
  }

  searchConsumableItems(): void {
    const query = this.consumableItemSearchText.trim();
    const requestId = ++this.itemSearchRequestId;

    if (query.length < 3) {
      this.consumableItems = [];
      this.isLoadingConsumableItems = false;
      if (!this.selectedConsumableItem || this.selectedConsumableItem.name !== this.consumableItemSearchText) {
        this.selectedConsumableItem = null;
      }
      this.cdr.markForCheck();
      return;
    }

    if (this.selectedConsumableItem && this.selectedConsumableItem.name !== query) {
      this.selectedConsumableItem = null;
    }

    this.isLoadingConsumableItems = true;
    this.cdr.markForCheck();
    this.http.get<ConsumableItemOption[]>(
      `/api/consumables/items/search?query=${encodeURIComponent(query)}`,
      { headers: this.buildActorHeaders() },
    ).subscribe({
      next: (items) => {
        if (requestId !== this.itemSearchRequestId) {
          return;
        }
        this.consumableItems = items;
        this.isLoadingConsumableItems = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        if (requestId !== this.itemSearchRequestId) {
          return;
        }
        console.error('Failed to search consumable items', err);
        this.consumableItems = [];
        this.isLoadingConsumableItems = false;
        this.cdr.markForCheck();
      },
    });
  }

  selectConsumableItem(item: ConsumableItemOption): void {
    this.selectedConsumableItem = item;
    this.consumableItemSearchText = item.name;
    this.consumableItems = [];
    this.isLoadingConsumableItems = false;
    this.itemSearchRequestId++;
    this.cdr.markForCheck();
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
    this.isLoadingConsumableItems = false;
    this.itemSearchRequestId++;
    this.cdr.markForCheck();
  }

  removeConsumableItem(itemId: number): void {
    this.selectedConsumableOrderItems = this.selectedConsumableOrderItems.filter((item) => item.itemId !== itemId);
    this.cdr.markForCheck();
  }

  getConsumableOrderTotal(): number {
    return this.selectedConsumableOrderItems.reduce((sum, item) => sum + item.totalCost, 0);
  }

  submitConsumableOrder(): void {
    if (!this.selectedConsumableUser || this.selectedConsumableOrderItems.length === 0 || this.isSubmittingConsumableOrder) {
      return;
    }

    this.isSubmittingConsumableOrder = true;
    this.cdr.markForCheck();
    this.http.post<{ orderId: number; totalAmount: number }>(
      '/api/consumables/order',
      {
        userId: this.selectedConsumableUser.id,
        items: this.selectedConsumableOrderItems.map((item) => ({
          itemId: item.itemId,
          quantity: item.quantity,
        })),
      },
      { headers: this.buildActorHeaders() },
    ).pipe(
      finalize(() => {
        this.isSubmittingConsumableOrder = false;
        this.cdr.markForCheck();
      }),
    ).subscribe({
      next: () => {
        this.selectedConsumableOrderItems = [];
        this.selectedConsumableItem = null;
        this.consumableItemSearchText = '';
        this.consumableItems = [];
        this.selectedConsumableQuantity = 1;
        this.selectedConsumableUser = null;
        this.consumableUserSearchText = '';
        this.consumableUsers = [];
        this.isLoadingConsumableUsers = false;
        this.isLoadingConsumableItems = false;
        this.userSearchRequestId++;
        this.itemSearchRequestId++;
        this.isSubmittingConsumableOrder = false;
        this.cdr.detectChanges();
        alert('Consumable order saved successfully');
      },
      error: (err) => {
        console.error('Failed to submit consumable order', err);
        this.isSubmittingConsumableOrder = false;
        this.cdr.detectChanges();
        alert('Unable to save consumable order right now');
      },
    });
  }

  private toNumber(value: number | string | null): number {
    if (value === null || value === undefined || value === '') {
      return 0;
    }

    return typeof value === 'number' ? value : Number(value);
  }

  private resetStateForBranchChange(): void {
    this.userSearchRequestId++;
    this.itemSearchRequestId++;
    this.consumableUserSearchText = '';
    this.consumableUsers = [];
    this.selectedConsumableUser = null;
    this.consumableItemSearchText = '';
    this.consumableItems = [];
    this.selectedConsumableItem = null;
    this.selectedConsumableQuantity = 1;
    this.selectedConsumableOrderItems = [];
    this.isLoadingConsumableUsers = false;
    this.isLoadingConsumableItems = false;
    this.isSubmittingConsumableOrder = false;
    this.cdr.markForCheck();
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
