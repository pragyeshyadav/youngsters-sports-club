import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { BrandTitleComponent } from '../../shared/components/brand-title/brand-title.component';
import { ClubLogoComponent } from '../../shared/components/club-logo/club-logo.component';
import { AuthService } from '../../core/services/auth.service';
import { OrganizationContextService } from '../../core/services/organization-context.service';
import { TriggerWhatsappPanelComponent } from '../../shared/components/trigger-whatsapp-panel/trigger-whatsapp-panel.component';

interface AdminMonthlyEarnings {
  currentMonthTotal: number | string | null;
  previousMonthTotal: number | string | null;
  snookerEarnings: number | string | null;
  snookerTableBreakdown: Record<string, number | string | null>;
  consumableEarnings: number | string | null;
  kidsZoneEarnings: number | string | null;
}

interface AdminUserAccess {
  id: number;
  role?: string;
}

interface SnookerBreakdownEntry {
  tableName: string;
  amount: number | string | null;
}

interface ConsumableItemOption {
  id: number;
  name: string;
  price: number | string | null;
}

interface ConsumableStockReportRow {
  itemId: number;
  itemName: string;
  stockAdded: number | string | null;
  soldQuantity: number | string | null;
  availableStock: number | string | null;
}

@Component({
  selector: 'app-admin-page',
  standalone: true,
  imports: [CommonModule, FormsModule, BrandTitleComponent, ClubLogoComponent, TriggerWhatsappPanelComponent],
  templateUrl: './admin-page.component.html',
  styleUrl: './admin-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminPageComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly organizationContext = inject(OrganizationContextService);

  canViewAdminReport = false;
  currentUserId: number | null = null;
  currentOrganizationId: number | null = null;
  currentBranchId: number | null = null;
  currentOrganizationName: string = '';
  isAddStockExpanded = false;
  isConsumableReportExpanded = false;
  isMonthlyReportExpanded = false;
  isLoadingStockItems = false;
  isSavingStock = false;
  isLoadingConsumableReport = false;
  isLoadingMonthlyReport = false;
  reportError = '';
  stockError = '';
  consumableReportError = '';
  selectedMonth = '';
  selectedYear = '';
  selectedConsumableReportMonth = '';
  selectedConsumableReportYear = '';
  stockItemSearchText = '';
  stockItems: ConsumableItemOption[] = [];
  selectedStockItem: ConsumableItemOption | null = null;
  selectedStockQuantity = 1;
  stockQuantityOptions = Array.from({ length: 100 }, (_, index) => index + 1);
  consumableStockReport: ConsumableStockReportRow[] = [];
  monthOptions = [
    { value: '01', label: 'January' },
    { value: '02', label: 'February' },
    { value: '03', label: 'March' },
    { value: '04', label: 'April' },
    { value: '05', label: 'May' },
    { value: '06', label: 'June' },
    { value: '07', label: 'July' },
    { value: '08', label: 'August' },
    { value: '09', label: 'September' },
    { value: '10', label: 'October' },
    { value: '11', label: 'November' },
    { value: '12', label: 'December' },
  ];
  yearOptions: string[] = [];
  snookerBreakdownEntries: SnookerBreakdownEntry[] = [];
  private stockItemSearchRequestId = 0;
  monthlyEarnings: AdminMonthlyEarnings = {
    currentMonthTotal: 0,
    previousMonthTotal: 0,
    snookerEarnings: 0,
    snookerTableBreakdown: {},
    consumableEarnings: 0,
    kidsZoneEarnings: 0,
  };

  ngOnInit(): void {
    const today = new Date();
    this.selectedMonth = `${today.getMonth() + 1}`.padStart(2, '0');
    this.selectedYear = `${today.getFullYear()}`;
    this.selectedConsumableReportMonth = this.selectedMonth;
    this.selectedConsumableReportYear = this.selectedYear;
    this.yearOptions = [this.selectedYear, `${today.getFullYear() - 1}`];

    const email = this.auth.getSnapshot()?.user.email;
    if (!email) {
      this.cdr.markForCheck();
      return;
    }

    this.organizationContext.context$.subscribe((context) => {
      const nextOrganizationId = context?.currentOrganization?.id ?? null;
      const nextBranchId = context?.currentBranch?.id ?? null;
      const contextChanged =
        this.currentOrganizationId !== nextOrganizationId || this.currentBranchId !== nextBranchId;

      this.currentOrganizationId = nextOrganizationId;
      this.currentBranchId = nextBranchId;
      this.currentOrganizationName = context?.currentOrganization?.name ?? '';
      if (contextChanged) {
        this.resetMonthlyReportState();
        if (this.isMonthlyReportExpanded) {
          this.loadMonthlyReport();
          return;
        }
      }
      this.cdr.markForCheck();
    });

    this.http.get<AdminUserAccess>(`/api/user?email=${encodeURIComponent(email)}`).subscribe({
      next: (user) => {
        this.currentUserId = user?.id ?? null;
        this.canViewAdminReport = ['ADMIN', 'SUPER_ADMIN'].includes(user?.role ?? '');
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load admin access', err);
        this.currentUserId = null;
        this.canViewAdminReport = false;
        this.cdr.markForCheck();
      },
    });
  }

  goBack(): void {
    void this.router.navigate(['/dashboard']);
  }

  goToClubSetupPortal(): void {
    void this.router.navigate(['/club-setup-portal']);
  }

  toggleMonthlyReport(): void {
    if (!this.canViewAdminReport) {
      return;
    }

    this.isMonthlyReportExpanded = !this.isMonthlyReportExpanded;
    if (this.isMonthlyReportExpanded) {
      this.loadMonthlyReport();
    }
  }

  toggleAddStockPanel(): void {
    if (!this.canViewAdminReport) {
      return;
    }
    this.isAddStockExpanded = !this.isAddStockExpanded;
    this.cdr.markForCheck();
  }

  toggleConsumableReport(): void {
    if (!this.canViewAdminReport) {
      return;
    }
    this.isConsumableReportExpanded = !this.isConsumableReportExpanded;
    if (this.isConsumableReportExpanded) {
      this.loadConsumableReport();
    } else {
      this.cdr.markForCheck();
    }
  }

  onFilterChange(): void {
    if (!this.isMonthlyReportExpanded) {
      return;
    }
    this.loadMonthlyReport();
  }

  onConsumableReportFilterChange(): void {
    if (!this.isConsumableReportExpanded) {
      return;
    }
    this.loadConsumableReport();
  }

  searchStockItems(): void {
    const query = this.stockItemSearchText.trim();
    const requestId = ++this.stockItemSearchRequestId;

    if (query.length < 3) {
      this.stockItems = [];
      this.isLoadingStockItems = false;
      if (!this.selectedStockItem || this.selectedStockItem.name !== this.stockItemSearchText) {
        this.selectedStockItem = null;
      }
      this.cdr.markForCheck();
      return;
    }

    if (this.selectedStockItem && this.selectedStockItem.name !== query) {
      this.selectedStockItem = null;
    }

    this.isLoadingStockItems = true;
    this.stockError = '';
    this.cdr.markForCheck();

    this.http.get<ConsumableItemOption[]>(
      `/api/consumables/items/search?query=${encodeURIComponent(query)}`,
      { headers: this.buildActorHeaders() },
    ).subscribe({
      next: (items) => {
        if (requestId !== this.stockItemSearchRequestId) {
          return;
        }
        this.stockItems = items;
        this.isLoadingStockItems = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        if (requestId !== this.stockItemSearchRequestId) {
          return;
        }
        console.error('Failed to search stock items', err);
        this.stockItems = [];
        this.isLoadingStockItems = false;
        this.stockError = 'Unable to search consumable items right now';
        this.cdr.markForCheck();
      },
    });
  }

  selectStockItem(item: ConsumableItemOption): void {
    this.selectedStockItem = item;
    this.stockItemSearchText = item.name;
    this.stockItems = [];
    this.isLoadingStockItems = false;
    this.stockItemSearchRequestId++;
    this.cdr.markForCheck();
  }

  canSaveStock(): boolean {
    return !!this.currentUserId && !!this.selectedStockItem && this.selectedStockQuantity > 0 && !this.isSavingStock;
  }

  saveStock(): void {
    if (!this.canSaveStock() || !this.selectedStockItem || !this.currentUserId) {
      return;
    }

    this.isSavingStock = true;
    this.stockError = '';
    this.cdr.markForCheck();

    this.http.post<{ message?: string }>(
      '/api/admin/consumables/stock',
      {
        itemId: this.selectedStockItem.id,
        quantityAdded: this.selectedStockQuantity,
        addedBy: this.currentUserId,
      },
      { headers: this.buildActorHeaders() },
    ).subscribe({
      next: (response) => {
        this.stockItemSearchText = '';
        this.stockItems = [];
        this.selectedStockItem = null;
        this.selectedStockQuantity = 1;
        this.isLoadingStockItems = false;
        this.isSavingStock = false;
        this.stockItemSearchRequestId++;
        if (this.isConsumableReportExpanded) {
          this.loadConsumableReport();
        } else {
          this.cdr.detectChanges();
        }
        alert(response?.message || 'Stock added successfully');
      },
      error: (err) => {
        console.error('Failed to save stock', err);
        this.stockError = err?.error?.message || 'Unable to add stock right now';
        this.isSavingStock = false;
        this.cdr.markForCheck();
      },
    });
  }

  getStockStateClass(availableStock: number | string | null): string {
    const stock = this.toNumber(availableStock);
    if (stock < 0) {
      return 'stock-negative';
    }
    if (stock < 10) {
      return 'stock-low';
    }
    return 'stock-positive';
  }

  protected loadMonthlyReport(): void {
    this.isLoadingMonthlyReport = true;
    this.reportError = '';
    this.cdr.markForCheck();

    this.http.get<AdminMonthlyEarnings>(`/api/admin/monthly-earnings?month=${this.selectedMonth}&year=${this.selectedYear}`).subscribe({
      next: (report) => {
        this.monthlyEarnings = {
          currentMonthTotal: report?.currentMonthTotal ?? 0,
          previousMonthTotal: report?.previousMonthTotal ?? 0,
          snookerEarnings: report?.snookerEarnings ?? 0,
          snookerTableBreakdown: report?.snookerTableBreakdown ?? {},
          consumableEarnings: report?.consumableEarnings ?? 0,
          kidsZoneEarnings: report?.kidsZoneEarnings ?? 0,
        };
        this.snookerBreakdownEntries = Object.entries(report?.snookerTableBreakdown ?? {}).map(([tableName, amount]) => ({
          tableName,
          amount,
        }));
        this.isLoadingMonthlyReport = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load monthly earnings report', err);
        this.resetMonthlyReportState();
        this.reportError = err?.error?.message || 'Unable to load monthly report right now';
        this.cdr.markForCheck();
      },
    });
  }

  protected resetMonthlyReportState(): void {
    this.monthlyEarnings = {
      currentMonthTotal: 0,
      previousMonthTotal: 0,
      snookerEarnings: 0,
      snookerTableBreakdown: {},
      consumableEarnings: 0,
      kidsZoneEarnings: 0,
    };
    this.snookerBreakdownEntries = [];
    this.isLoadingMonthlyReport = false;
  }

  private loadConsumableReport(): void {
    this.isLoadingConsumableReport = true;
    this.consumableReportError = '';
    this.cdr.markForCheck();

    this.http.get<ConsumableStockReportRow[]>(
      `/api/admin/consumables/stock-report?month=${this.selectedConsumableReportMonth}&year=${this.selectedConsumableReportYear}`,
      { headers: this.buildActorHeaders() },
    ).subscribe({
      next: (report) => {
        this.consumableStockReport = report ?? [];
        this.isLoadingConsumableReport = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load consumable stock report', err);
        this.consumableStockReport = [];
        this.consumableReportError = err?.error?.message || 'Unable to load consumable stock report right now';
        this.isLoadingConsumableReport = false;
        this.cdr.markForCheck();
      },
    });
  }

  private toNumber(value: number | string | null): number {
    if (value === null || value === undefined || value === '') {
      return 0;
    }

    const parsed = typeof value === 'number' ? value : Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  private buildActorHeaders(): HttpHeaders {
    const actorEmail = this.auth.getSnapshot()?.user.email ?? this.getStoredUserEmail();
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
