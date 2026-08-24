import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { BrandTitleComponent } from '../../shared/components/brand-title/brand-title.component';
import { ClubLogoComponent } from '../../shared/components/club-logo/club-logo.component';
import { AuthService } from '../../core/services/auth.service';
import { OrganizationContextService } from '../../core/services/organization-context.service';

interface SnookerTableAdminRow {
  id: number;
  tableName: string;
  ratePerMinute: number | string | null;
  active?: boolean;
  available?: boolean;
}

interface ConsumableItemAdminRow {
  id: number;
  name: string;
  price: number | string | null;
  active?: boolean;
}

interface ManagerBranchAccess {
  branchId: number;
  branchName: string;
  baseBranch: boolean;
  granted: boolean;
}

interface ManagerAdminRow {
  organizationUserId: number;
  userId: number;
  name: string;
  email?: string | null;
  phone?: string | null;
  role?: string | null;
  active?: boolean;
  baseBranchId?: number | null;
  branchAccesses: ManagerBranchAccess[];
}

interface PromoteSearchRow {
  id: number;
  name?: string;
  email?: string;
  phone?: string;
}

interface BranchOption {
  id: number;
  name: string;
}

@Component({
  selector: 'app-club-setup-portal',
  standalone: true,
  imports: [CommonModule, FormsModule, BrandTitleComponent, ClubLogoComponent],
  templateUrl: './club-setup-portal.component.html',
  styleUrl: './club-setup-portal.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClubSetupPortalComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly organizationContext = inject(OrganizationContextService);

  canManageClub = false;

  isTablesPanelExpanded = false;
  isItemsPanelExpanded = false;
  isManagersPanelExpanded = false;

  currentOrganizationName: string = '';

  tables: SnookerTableAdminRow[] = [];
  isLoadingTables = false;
  tablesError = '';
  newTableName = '';
  newTableRate: number | null = null;
  isSavingTable = false;
  editingTableId: number | null = null;
  editTableName = '';
  editTableRate: number | null = null;
  busyTableId: number | null = null;

  items: ConsumableItemAdminRow[] = [];
  isLoadingItems = false;
  itemsError = '';
  newItemName = '';
  newItemPrice: number | null = null;
  isSavingItem = false;
  editingItemId: number | null = null;
  editItemName = '';
  editItemPrice: number | null = null;
  busyItemId: number | null = null;

  managers: ManagerAdminRow[] = [];
  isLoadingManagers = false;
  managersError = '';
  managerSearchText = '';
  managerSearchResults: PromoteSearchRow[] = [];
  isSearchingManagers = false;
  selectedPromoteUser: PromoteSearchRow | null = null;
  isPromoting = false;
  accessEditorOrgUserId: number | null = null;
  actorBranches: BranchOption[] = [];
  busyAccessKey = '';

  private managerSearchRequestId = 0;
  private currentBranchId: number | null = null;
  private contextSubscription: Subscription | null = null;

  ngOnInit(): void {
    this.contextSubscription = this.organizationContext.currentContext$.subscribe((context) => {
      const branchId = context?.currentBranch?.id ?? null;
      if (this.currentBranchId !== branchId) {
        this.currentBranchId = branchId;
        this.resetForBranchChange();
      }
      if (context?.accessibleBranches?.length) {
        this.actorBranches = context.accessibleBranches.map((branch) => ({ id: branch.id, name: branch.name }));
        this.cdr.markForCheck();
      }
      this.currentOrganizationName = context?.currentOrganization?.name ?? '';
      this.cdr.markForCheck();
    });

    const email = this.auth.getSnapshot()?.user.email;
    if (!email) {
      this.cdr.markForCheck();
      return;
    }

    this.http
      .get<{ id?: number; role?: string }>(`/api/user?email=${encodeURIComponent(email)}`)
      .subscribe({
        next: (user) => {
          this.canManageClub = ['ADMIN', 'SUPER_ADMIN'].includes(user?.role ?? '');
          this.cdr.markForCheck();
        },
        error: () => {
          this.canManageClub = false;
          this.cdr.markForCheck();
        },
      });
  }

  ngOnDestroy(): void {
    this.contextSubscription?.unsubscribe();
  }

  goBack(): void {
    void this.router.navigate(['/admin-page']);
  }

  toggleTablesPanel(): void {
    if (!this.canManageClub) {
      return;
    }
    this.isTablesPanelExpanded = !this.isTablesPanelExpanded;
    if (this.isTablesPanelExpanded && this.tables.length === 0) {
      this.loadTables();
    } else {
      this.cdr.markForCheck();
    }
  }

  toggleItemsPanel(): void {
    if (!this.canManageClub) {
      return;
    }
    this.isItemsPanelExpanded = !this.isItemsPanelExpanded;
    if (this.isItemsPanelExpanded && this.items.length === 0) {
      this.loadItems();
    } else {
      this.cdr.markForCheck();
    }
  }

  toggleManagersPanel(): void {
    if (!this.canManageClub) {
      return;
    }
    this.isManagersPanelExpanded = !this.isManagersPanelExpanded;
    if (this.isManagersPanelExpanded && this.managers.length === 0) {
      this.loadManagers();
    } else {
      this.cdr.markForCheck();
    }
  }

  loadTables(): void {
    this.isLoadingTables = true;
    this.tablesError = '';
    this.cdr.markForCheck();

    this.http.get<SnookerTableAdminRow[]>('/api/snooker/tables/manage', { headers: this.buildActorHeaders() }).subscribe({
      next: (tables) => {
        this.tables = tables ?? [];
        this.isLoadingTables = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load snooker tables', err);
        this.tables = [];
        this.tablesError = err?.error?.message || 'Unable to load snooker tables right now';
        this.isLoadingTables = false;
        this.cdr.markForCheck();
      },
    });
  }

  canSaveNewTable(): boolean {
    return !!this.newTableName.trim() && (this.newTableRate ?? 0) > 0 && !this.isSavingTable;
  }

  createTable(): void {
    if (!this.canSaveNewTable()) {
      return;
    }

    this.isSavingTable = true;
    this.tablesError = '';
    this.cdr.markForCheck();

    this.http
      .post<SnookerTableAdminRow>(
        '/api/snooker/tables',
        { tableName: this.newTableName.trim(), ratePerMinute: this.newTableRate },
        { headers: this.buildActorHeaders() },
      )
      .subscribe({
        next: (table) => {
          if (table?.id) {
            this.tables = [...this.tables, table].sort((a, b) => a.id - b.id);
          }
          this.resetNewTableForm();
          alert('Snooker table added successfully');
        },
        error: (err) => {
          console.error('Failed to add snooker table', err);
          this.tablesError = err?.error?.message || 'Unable to add snooker table right now';
          this.isSavingTable = false;
          this.cdr.markForCheck();
        },
      });
  }

  startEditTable(table: SnookerTableAdminRow): void {
    this.editingTableId = table.id;
    this.editTableName = table.tableName;
    this.editTableRate = Number(table.ratePerMinute ?? 0) || null;
    this.tablesError = '';
    this.cdr.markForCheck();
  }

  cancelEditTable(): void {
    this.editingTableId = null;
    this.editTableName = '';
    this.editTableRate = null;
    this.cdr.markForCheck();
  }

  saveTableEdit(table: SnookerTableAdminRow): void {
    if (!this.editTableName.trim() || (this.editTableRate ?? 0) <= 0) {
      return;
    }

    this.busyTableId = table.id;
    this.tablesError = '';
    this.cdr.markForCheck();

    this.http
      .put<SnookerTableAdminRow>(
        `/api/snooker/tables/${table.id}`,
        { tableName: this.editTableName.trim(), ratePerMinute: this.editTableRate },
        { headers: this.buildActorHeaders() },
      )
      .subscribe({
        next: (updated) => {
          this.tables = this.tables.map((row) => (row.id === table.id ? { ...row, ...updated } : row));
          this.cancelEditTable();
          this.busyTableId = null;
          alert('Snooker table updated successfully');
        },
        error: (err) => {
          console.error('Failed to update snooker table', err);
          this.tablesError = err?.error?.message || 'Unable to update snooker table right now';
          this.busyTableId = null;
          this.cdr.markForCheck();
        },
      });
  }

  toggleTableActive(table: SnookerTableAdminRow): void {
    const nextState = !table.active;
    this.busyTableId = table.id;
    this.tablesError = '';
    this.cdr.markForCheck();

    this.http
      .put<SnookerTableAdminRow>(`/api/snooker/tables/${table.id}/active`, { isActive: nextState }, { headers: this.buildActorHeaders() })
      .subscribe({
        next: (updated) => {
          this.tables = this.tables.map((row) => (row.id === table.id ? { ...row, ...updated } : row));
          this.busyTableId = null;
          alert(nextState ? 'Snooker table activated' : 'Snooker table deactivated');
        },
        error: (err) => {
          console.error('Failed to change snooker table state', err);
          this.tablesError = err?.error?.message || 'Unable to change table state right now';
          this.busyTableId = null;
          this.cdr.markForCheck();
        },
      });
  }

  releaseTable(table: SnookerTableAdminRow): void {
    if (!window.confirm(`Release "${table.tableName}" for new frames? Only do this if the table is stuck.`)) {
      return;
    }

    this.busyTableId = table.id;
    this.tablesError = '';
    this.cdr.markForCheck();

    this.http.post<SnookerTableAdminRow>(`/api/snooker/tables/${table.id}/release`, {}, { headers: this.buildActorHeaders() }).subscribe({
      next: (updated) => {
        this.tables = this.tables.map((row) => (row.id === table.id ? { ...row, ...updated } : row));
        this.busyTableId = null;
        alert('Table availability released');
      },
      error: (err) => {
        console.error('Failed to release snooker table', err);
        this.tablesError = err?.error?.message || 'Unable to release table right now';
        this.busyTableId = null;
        this.cdr.markForCheck();
      },
    });
  }

  loadItems(): void {
    this.isLoadingItems = true;
    this.itemsError = '';
    this.cdr.markForCheck();

    this.http.get<ConsumableItemAdminRow[]>('/api/admin/consumables/items', { headers: this.buildActorHeaders() }).subscribe({
      next: (items) => {
        this.items = items ?? [];
        this.isLoadingItems = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load consumable items', err);
        this.items = [];
        this.itemsError = err?.error?.message || 'Unable to load consumable items right now';
        this.isLoadingItems = false;
        this.cdr.markForCheck();
      },
    });
  }

  canSaveNewItem(): boolean {
    return !!this.newItemName.trim() && (this.newItemPrice ?? 0) > 0 && !this.isSavingItem;
  }

  createItem(): void {
    if (!this.canSaveNewItem()) {
      return;
    }

    this.isSavingItem = true;
    this.itemsError = '';
    this.cdr.markForCheck();

    this.http
      .post<ConsumableItemAdminRow>(
        '/api/admin/consumables/items',
        { name: this.newItemName.trim(), price: this.newItemPrice },
        { headers: this.buildActorHeaders() },
      )
      .subscribe({
        next: (item) => {
          if (item?.id) {
            this.items = [...this.items, item].sort((a, b) => a.name.localeCompare(b.name));
          }
          this.resetNewItemForm();
          alert('Consumable item added successfully');
        },
        error: (err) => {
          console.error('Failed to add consumable item', err);
          this.itemsError = err?.error?.message || 'Unable to add consumable item right now';
          this.isSavingItem = false;
          this.cdr.markForCheck();
        },
      });
  }

  startEditItem(item: ConsumableItemAdminRow): void {
    this.editingItemId = item.id;
    this.editItemName = item.name;
    this.editItemPrice = Number(item.price ?? 0) || null;
    this.itemsError = '';
    this.cdr.markForCheck();
  }

  cancelEditItem(): void {
    this.editingItemId = null;
    this.editItemName = '';
    this.editItemPrice = null;
    this.cdr.markForCheck();
  }

  saveItemEdit(item: ConsumableItemAdminRow): void {
    if (!this.editItemName.trim() || (this.editItemPrice ?? 0) <= 0) {
      return;
    }

    this.busyItemId = item.id;
    this.itemsError = '';
    this.cdr.markForCheck();

    this.http
      .put<ConsumableItemAdminRow>(
        `/api/admin/consumables/items/${item.id}`,
        { name: this.editItemName.trim(), price: this.editItemPrice },
        { headers: this.buildActorHeaders() },
      )
      .subscribe({
        next: (updated) => {
          this.items = this.items.map((row) => (row.id === item.id ? { ...row, ...updated } : row));
          this.cancelEditItem();
          this.busyItemId = null;
          alert('Consumable item updated successfully');
        },
        error: (err) => {
          console.error('Failed to update consumable item', err);
          this.itemsError = err?.error?.message || 'Unable to update consumable item right now';
          this.busyItemId = null;
          this.cdr.markForCheck();
        },
      });
  }

  toggleItemActive(item: ConsumableItemAdminRow): void {
    const nextState = !item.active;
    this.busyItemId = item.id;
    this.itemsError = '';
    this.cdr.markForCheck();

    this.http
      .put<ConsumableItemAdminRow>(
        `/api/admin/consumables/items/${item.id}/active`,
        { isActive: nextState },
        { headers: this.buildActorHeaders() },
      )
      .subscribe({
        next: (updated) => {
          this.items = this.items.map((row) => (row.id === item.id ? { ...row, ...updated } : row));
          this.busyItemId = null;
          alert(nextState ? 'Consumable item activated' : 'Consumable item deactivated');
        },
        error: (err) => {
          console.error('Failed to change consumable item state', err);
          this.itemsError = err?.error?.message || 'Unable to change item state right now';
          this.busyItemId = null;
          this.cdr.markForCheck();
        },
      });
  }

  loadManagers(): void {
    this.isLoadingManagers = true;
    this.managersError = '';
    this.cdr.markForCheck();

    this.http.get<ManagerAdminRow[]>('/api/managers/current-branch', { headers: this.buildActorHeaders() }).subscribe({
      next: (managers) => {
        this.managers = managers ?? [];
        this.isLoadingManagers = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load managers', err);
        this.managers = [];
        this.managersError = err?.error?.message || 'Unable to load managers right now';
        this.isLoadingManagers = false;
        this.cdr.markForCheck();
      },
    });
  }

  searchPromoteCandidates(): void {
    const query = this.managerSearchText.trim();
    const requestId = ++this.managerSearchRequestId;

    if (query.length < 3) {
      this.managerSearchResults = [];
      this.isSearchingManagers = false;
      if (this.selectedPromoteUser && this.selectedPromoteUser.name !== this.managerSearchText) {
        this.selectedPromoteUser = null;
      }
      this.cdr.markForCheck();
      return;
    }

    if (this.selectedPromoteUser && this.selectedPromoteUser.name !== query) {
      this.selectedPromoteUser = null;
    }

    this.isSearchingManagers = true;
    this.managersError = '';
    this.cdr.markForCheck();

    this.http
      .get<PromoteSearchRow[]>(`/api/users/search/current-branch?query=${encodeURIComponent(query)}`, {
        headers: this.buildActorHeaders(),
      })
      .subscribe({
        next: (users) => {
          if (requestId !== this.managerSearchRequestId) {
            return;
          }
          this.managerSearchResults = users ?? [];
          this.isSearchingManagers = false;
          this.cdr.markForCheck();
        },
        error: (err) => {
          if (requestId !== this.managerSearchRequestId) {
            return;
          }
          console.error('Failed to search users', err);
          this.managerSearchResults = [];
          this.isSearchingManagers = false;
          this.cdr.markForCheck();
        },
      });
  }

  selectPromoteCandidate(user: PromoteSearchRow): void {
    this.selectedPromoteUser = user;
    this.managerSearchText = user.name ?? '';
    this.managerSearchResults = [];
    this.isSearchingManagers = false;
    this.managerSearchRequestId++;
    this.cdr.markForCheck();
  }

  promoteSelectedUser(): void {
    if (!this.selectedPromoteUser || this.isPromoting) {
      return;
    }

    this.isPromoting = true;
    this.managersError = '';
    this.cdr.markForCheck();

    this.http
      .post<ManagerAdminRow>('/api/managers/promote', { userId: this.selectedPromoteUser.id }, { headers: this.buildActorHeaders() })
      .subscribe({
        next: (manager) => {
          this.managerSearchText = '';
          this.selectedPromoteUser = null;
          this.managerSearchResults = [];
          this.isPromoting = false;
          this.managerSearchRequestId++;
          if (manager?.organizationUserId) {
            this.upsertManagerRow(manager);
          } else {
            this.loadManagers();
          }
          alert('User promoted to manager');
        },
        error: (err) => {
          console.error('Failed to promote user', err);
          this.managersError = err?.error?.message || 'Unable to promote user right now';
          this.isPromoting = false;
          this.cdr.markForCheck();
        },
      });
  }

  demoteManager(manager: ManagerAdminRow): void {
    if (!window.confirm(`Demote "${manager.name}" back to customer?`)) {
      return;
    }

    this.busyAccessKey = `demote-${manager.organizationUserId}`;
    this.managersError = '';
    this.cdr.markForCheck();

    this.http
      .post<ManagerAdminRow>(`/api/managers/${manager.organizationUserId}/demote`, {}, { headers: this.buildActorHeaders() })
      .subscribe({
        next: (updated) => {
          if (updated?.organizationUserId) {
            this.removeManagerRow(manager.organizationUserId);
          } else {
            this.loadManagers();
          }
          this.busyAccessKey = '';
          alert('Manager demoted to customer');
        },
        error: (err) => {
          console.error('Failed to demote manager', err);
          this.managersError = err?.error?.message || 'Unable to demote manager right now';
          this.busyAccessKey = '';
          this.cdr.markForCheck();
        },
      });
  }

  deactivateManager(manager: ManagerAdminRow): void {
    if (!window.confirm(`Deactivate "${manager.name}"? They will lose access to this organization.`)) {
      return;
    }

    this.busyAccessKey = `deactivate-${manager.organizationUserId}`;
    this.managersError = '';
    this.cdr.markForCheck();

    this.http
      .post<{ message?: string }>(`/api/managers/${manager.organizationUserId}/deactivate`, {}, { headers: this.buildActorHeaders() })
      .subscribe({
        next: () => {
          this.removeManagerRow(manager.organizationUserId);
          this.busyAccessKey = '';
          alert('Manager deactivated');
        },
        error: (err) => {
          console.error('Failed to deactivate manager', err);
          this.managersError = err?.error?.message || 'Unable to deactivate manager right now';
          this.busyAccessKey = '';
          this.cdr.markForCheck();
        },
      });
  }

  toggleAccessEditor(manager: ManagerAdminRow): void {
    this.accessEditorOrgUserId = this.accessEditorOrgUserId === manager.organizationUserId ? null : manager.organizationUserId;
    this.managersError = '';
    this.cdr.markForCheck();
  }

  isBranchGranted(manager: ManagerAdminRow, branchId: number): boolean {
    const access = manager.branchAccesses.find((entry) => entry.branchId === branchId);
    return !!access?.granted || manager.baseBranchId === branchId;
  }

  isBaseBranch(manager: ManagerAdminRow, branchId: number): boolean {
    return manager.baseBranchId === branchId;
  }

  setBranchAccess(manager: ManagerAdminRow, branchId: number, granted: boolean): void {
    if (this.isBaseBranch(manager, branchId)) {
      return;
    }

    const key = `${manager.organizationUserId}-${branchId}`;
    this.busyAccessKey = key;
    this.managersError = '';
    this.cdr.markForCheck();

    this.http
      .put<ManagerBranchAccess>(
        `/api/managers/${manager.organizationUserId}/branch-access`,
        { branchId, granted },
        { headers: this.buildActorHeaders() },
      )
      .subscribe({
        next: (access) => {
          this.managers = this.managers.map((row) =>
            row.organizationUserId === manager.organizationUserId
              ? this.withUpdatedAccess(row, access)
              : row,
          );
          this.busyAccessKey = '';
          alert(granted ? 'Branch access granted' : 'Branch access revoked');
        },
        error: (err) => {
          console.error('Failed to update branch access', err);
          this.managersError = err?.error?.message || 'Unable to update branch access right now';
          this.busyAccessKey = '';
          this.cdr.markForCheck();
        },
      });
  }

  trackTable(_: number, table: SnookerTableAdminRow): number {
    return table.id;
  }

  trackItem(_: number, item: ConsumableItemAdminRow): number {
    return item.id;
  }

  trackManager(_: number, manager: ManagerAdminRow): number {
    return manager.organizationUserId;
  }

  private withUpdatedAccess(manager: ManagerAdminRow, access: ManagerBranchAccess): ManagerAdminRow {
    if (access.baseBranch) {
      return manager;
    }
    const rest = manager.branchAccesses.filter((entry) => entry.branchId !== access.branchId || entry.baseBranch);
    return { ...manager, branchAccesses: [...rest, access] };
  }

  private upsertManagerRow(manager: ManagerAdminRow): void {
    const exists = this.managers.some((row) => row.organizationUserId === manager.organizationUserId);
    this.managers = exists
      ? this.managers.map((row) => (row.organizationUserId === manager.organizationUserId ? manager : row))
      : [...this.managers, manager];
    this.cdr.markForCheck();
  }

  private removeManagerRow(organizationUserId: number): void {
    this.managers = this.managers.filter((row) => row.organizationUserId !== organizationUserId);
    if (this.accessEditorOrgUserId === organizationUserId) {
      this.accessEditorOrgUserId = null;
    }
    this.cdr.markForCheck();
  }

  private resetNewTableForm(): void {
    this.newTableName = '';
    this.newTableRate = null;
    this.isSavingTable = false;
    this.cdr.detectChanges();
  }

  private resetNewItemForm(): void {
    this.newItemName = '';
    this.newItemPrice = null;
    this.isSavingItem = false;
    this.cdr.detectChanges();
  }

  private resetForBranchChange(): void {
    this.isTablesPanelExpanded = false;
    this.isItemsPanelExpanded = false;
    this.isManagersPanelExpanded = false;
    this.tables = [];
    this.items = [];
    this.managers = [];
    this.tablesError = '';
    this.itemsError = '';
    this.managersError = '';
    this.isLoadingTables = false;
    this.isLoadingItems = false;
    this.isLoadingManagers = false;
    this.cancelEditTable();
    this.cancelEditItem();
    this.accessEditorOrgUserId = null;
    this.managerSearchText = '';
    this.managerSearchResults = [];
    this.selectedPromoteUser = null;
    this.managerSearchRequestId++;
    this.cdr.markForCheck();
  }

  private buildActorHeaders(): HttpHeaders {
    const actorEmail = this.auth.getSnapshot()?.user.email;
    return actorEmail ? new HttpHeaders({ 'X-User-Email': actorEmail.trim() }) : new HttpHeaders();
  }
}
