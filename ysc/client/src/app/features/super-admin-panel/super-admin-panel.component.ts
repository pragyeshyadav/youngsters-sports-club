import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { OrganizationContextService } from '../../core/services/organization-context.service';
import { BrandTitleComponent } from '../../shared/components/brand-title/brand-title.component';
import { ClubLogoComponent } from '../../shared/components/club-logo/club-logo.component';

type AssignmentSection = 'grant' | 'onboard';

interface OrganizationOption {
  id: number;
  name: string;
  logoUrl?: string | null;
}

interface OrganizationAdminRow {
  id: number;
  name: string;
  logoUrl?: string | null;
  phone?: string | null;
  email?: string | null;
  address?: string | null;
  city?: string | null;
  state?: string | null;
  country?: string | null;
  active: boolean;
}

interface BranchAdminRow {
  id: number;
  organizationId: number;
  organizationName?: string | null;
  name: string;
  branchCode?: string | null;
  address?: string | null;
  city?: string | null;
  state?: string | null;
  phone?: string | null;
  email?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  active: boolean;
}

interface PortalContextResponse {
  organizations: OrganizationOption[];
  assignableRoles: string[];
}

interface UserSearchResult {
  id: number;
  name?: string | null;
  email?: string | null;
  phone?: string | null;
}

interface AccessibleBranch {
  id: number;
  name: string;
}

interface MembershipSummary {
  organizationId: number;
  organizationName: string;
  role?: string | null;
  active: boolean;
  baseBranchId?: number | null;
  baseBranchName?: string | null;
  accessibleBranches: AccessibleBranch[];
}

interface CandidateDetails {
  userId: number;
  name?: string | null;
  email?: string | null;
  phone?: string | null;
  memberships: MembershipSummary[];
}

interface AssignmentState {
  organizationId: number | null;
  searchText: string;
  searchResults: UserSearchResult[];
  isSearching: boolean;
  selectedUser: UserSearchResult | null;
  candidate: CandidateDetails | null;
  role: string | null;
  baseBranchId: number | null;
  selectedBranchIds: number[];
  branches: BranchAdminRow[];
  isLoadingBranches: boolean;
  isSaving: boolean;
  successMessage: string;
  errorMessage: string;
}

interface OrganizationFormState {
  name: string;
  logoUrl: string;
  phone: string;
  email: string;
  address: string;
  city: string;
  state: string;
  country: string;
}

interface BranchFormState {
  organizationId: number | null;
  name: string;
  branchCode: string;
  address: string;
  city: string;
  state: string;
  phone: string;
  email: string;
  latitude: string;
  longitude: string;
  isActive: boolean;
}

@Component({
  selector: 'app-super-admin-panel',
  standalone: true,
  imports: [CommonModule, FormsModule, BrandTitleComponent, ClubLogoComponent],
  templateUrl: './super-admin-panel.component.html',
  styleUrl: './super-admin-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SuperAdminPanelComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly organizationContext = inject(OrganizationContextService);

  currentOrganizationName = '';
  isLoadingPortal = false;
  portalError = '';
  organizations: OrganizationAdminRow[] = [];
  activeOrganizations: OrganizationOption[] = [];
  assignableRoles: string[] = [];

  organizationsExpanded = true;
  branchesExpanded = true;
  grantAccessExpanded = true;
  onboardStaffExpanded = true;

  isLoadingOrganizations = false;
  organizationFormExpanded = false;
  editingOrganizationId: number | null = null;
  isSavingOrganization = false;
  organizationMessage = '';
  organizationError = '';
  organizationForm: OrganizationFormState = this.createEmptyOrganizationForm();

  selectedBranchesOrganizationId: number | null = null;
  branches: BranchAdminRow[] = [];
  isLoadingBranches = false;
  branchFormExpanded = false;
  editingBranchId: number | null = null;
  isSavingBranch = false;
  branchMessage = '';
  branchError = '';
  branchForm: BranchFormState = this.createEmptyBranchForm();

  readonly grantState = this.createAssignmentState();
  readonly onboardState = this.createAssignmentState();

  private contextSubscription: Subscription | null = null;
  private readonly searchTimers: Partial<Record<AssignmentSection, ReturnType<typeof setTimeout>>> = {};

  ngOnInit(): void {
    this.contextSubscription = this.organizationContext.currentContext$.subscribe((context) => {
      this.currentOrganizationName = context?.currentOrganization?.name ?? '';
      this.cdr.markForCheck();
    });

    this.loadPortal();
  }

  ngOnDestroy(): void {
    this.contextSubscription?.unsubscribe();
    for (const timer of Object.values(this.searchTimers)) {
      if (timer) {
        clearTimeout(timer);
      }
    }
  }

  goBack(): void {
    void this.router.navigate(['/dashboard']);
  }

  togglePanel(panel: 'organizations' | 'branches' | 'grant' | 'onboard'): void {
    if (panel === 'organizations') {
      this.organizationsExpanded = !this.organizationsExpanded;
    } else if (panel === 'branches') {
      this.branchesExpanded = !this.branchesExpanded;
    } else if (panel === 'grant') {
      this.grantAccessExpanded = !this.grantAccessExpanded;
    } else {
      this.onboardStaffExpanded = !this.onboardStaffExpanded;
    }
  }

  toggleOrganizationForm(): void {
    this.organizationFormExpanded = !this.organizationFormExpanded;
    if (!this.organizationFormExpanded) {
      this.resetOrganizationForm();
    }
  }

  startEditOrganization(organization: OrganizationAdminRow): void {
    this.organizationFormExpanded = true;
    this.editingOrganizationId = organization.id;
    this.organizationError = '';
    this.organizationMessage = '';
    this.organizationForm = {
      name: organization.name ?? '',
      logoUrl: organization.logoUrl ?? '',
      phone: organization.phone ?? '',
      email: organization.email ?? '',
      address: organization.address ?? '',
      city: organization.city ?? '',
      state: organization.state ?? '',
      country: organization.country ?? '',
    };
  }

  saveOrganization(): void {
    if (!this.organizationForm.name.trim() || this.isSavingOrganization) {
      return;
    }

    const actorEmail = this.getActorEmail();
    if (!actorEmail) {
      this.portalError = 'Authenticated actor email is missing';
      return;
    }

    this.isSavingOrganization = true;
    this.organizationError = '';
    this.organizationMessage = '';
    this.cdr.markForCheck();

    const payload = {
      actorEmail,
      ...this.organizationForm,
    };

    const request$ = this.editingOrganizationId == null
      ? this.http.post<OrganizationAdminRow>('/api/super-admin/organizations', payload, { headers: this.buildActorHeaders() })
      : this.http.put<OrganizationAdminRow>(`/api/super-admin/organizations/${this.editingOrganizationId}`, payload, {
          headers: this.buildActorHeaders(),
        });

    request$.subscribe({
      next: () => {
        this.isSavingOrganization = false;
        this.organizationMessage = this.editingOrganizationId == null
          ? 'Organization created successfully'
          : 'Organization updated successfully';
        this.resetOrganizationForm();
        this.loadOrganizations();
      },
      error: (err) => {
        this.isSavingOrganization = false;
        this.organizationError = err?.error?.message || 'Unable to save organization right now';
        this.cdr.markForCheck();
      },
    });
  }

  deactivateOrganization(organization: OrganizationAdminRow): void {
    const actorEmail = this.getActorEmail();
    if (!actorEmail || !organization?.id) {
      return;
    }
    this.organizationError = '';
    this.organizationMessage = '';
    this.http
      .post(`/api/super-admin/organizations/${organization.id}/deactivate?email=${encodeURIComponent(actorEmail)}`, {}, {
        headers: this.buildActorHeaders(),
      })
      .subscribe({
        next: () => {
          this.organizationMessage = `${organization.name} deactivated successfully`;
          this.loadOrganizations();
        },
        error: (err) => {
          this.organizationError = err?.error?.message || 'Unable to deactivate organization right now';
          this.cdr.markForCheck();
        },
      });
  }

  onBranchesOrganizationChange(organizationId: number | null): void {
    this.selectedBranchesOrganizationId = organizationId;
    this.branchForm.organizationId = organizationId;
    this.editingBranchId = null;
    this.branchMessage = '';
    this.branchError = '';
    this.branches = [];
    if (organizationId) {
      this.loadBranchesForOrganization(organizationId);
    } else {
      this.cdr.markForCheck();
    }
  }

  toggleBranchForm(): void {
    this.branchFormExpanded = !this.branchFormExpanded;
    if (!this.branchFormExpanded) {
      this.resetBranchForm();
    }
  }

  startEditBranch(branch: BranchAdminRow): void {
    this.branchFormExpanded = true;
    this.editingBranchId = branch.id;
    this.branchError = '';
    this.branchMessage = '';
    this.branchForm = {
      organizationId: branch.organizationId,
      name: branch.name ?? '',
      branchCode: branch.branchCode ?? '',
      address: branch.address ?? '',
      city: branch.city ?? '',
      state: branch.state ?? '',
      phone: branch.phone ?? '',
      email: branch.email ?? '',
      latitude: branch.latitude == null ? '' : String(branch.latitude),
      longitude: branch.longitude == null ? '' : String(branch.longitude),
      isActive: branch.active,
    };
  }

  saveBranch(): void {
    if (!this.branchForm.organizationId || !this.branchForm.name.trim() || this.isSavingBranch) {
      return;
    }

    const actorEmail = this.getActorEmail();
    if (!actorEmail) {
      this.portalError = 'Authenticated actor email is missing';
      return;
    }

    this.isSavingBranch = true;
    this.branchError = '';
    this.branchMessage = '';
    this.cdr.markForCheck();

    const payload = {
      actorEmail,
      organizationId: this.branchForm.organizationId,
      name: this.branchForm.name,
      branchCode: this.branchForm.branchCode,
      address: this.branchForm.address,
      city: this.branchForm.city,
      state: this.branchForm.state,
      phone: this.branchForm.phone,
      email: this.branchForm.email,
      latitude: this.parseDecimal(this.branchForm.latitude),
      longitude: this.parseDecimal(this.branchForm.longitude),
      isActive: this.branchForm.isActive,
    };

    const request$ = this.editingBranchId == null
      ? this.http.post<BranchAdminRow>('/api/super-admin/branches', payload, { headers: this.buildActorHeaders() })
      : this.http.put<BranchAdminRow>(`/api/super-admin/branches/${this.editingBranchId}`, payload, {
          headers: this.buildActorHeaders(),
        });

    request$.subscribe({
      next: () => {
        this.isSavingBranch = false;
        this.branchMessage = this.editingBranchId == null
          ? 'Branch created successfully'
          : 'Branch updated successfully';
        const currentOrganizationId = this.branchForm.organizationId;
        this.resetBranchForm();
        if (currentOrganizationId) {
          this.selectedBranchesOrganizationId = currentOrganizationId;
          this.loadBranchesForOrganization(currentOrganizationId);
        }
      },
      error: (err) => {
        this.isSavingBranch = false;
        this.branchError = err?.error?.message || 'Unable to save branch right now';
        this.cdr.markForCheck();
      },
    });
  }

  deactivateBranch(branch: BranchAdminRow): void {
    const actorEmail = this.getActorEmail();
    if (!actorEmail || !branch?.id) {
      return;
    }

    this.branchError = '';
    this.branchMessage = '';
    this.http.post(
      `/api/super-admin/branches/${branch.id}/deactivate?organizationId=${branch.organizationId}&email=${encodeURIComponent(actorEmail)}`,
      {},
      { headers: this.buildActorHeaders() },
    ).subscribe({
      next: () => {
        this.branchMessage = `${branch.name} deactivated successfully`;
        this.loadBranchesForOrganization(branch.organizationId);
      },
      error: (err) => {
        this.branchError = err?.error?.message || 'Unable to deactivate branch right now';
        this.cdr.markForCheck();
      },
    });
  }

  onAssignmentOrganizationChange(section: AssignmentSection, organizationId: number | null): void {
    const state = this.getAssignmentState(section);
    state.organizationId = organizationId;
    state.searchText = '';
    state.searchResults = [];
    state.selectedUser = null;
    state.candidate = null;
    state.successMessage = '';
    state.errorMessage = '';
    state.role = null;
    state.baseBranchId = null;
    state.selectedBranchIds = [];
    state.branches = [];
    if (organizationId) {
      this.loadAssignmentBranches(section, organizationId);
    }
    this.cdr.markForCheck();
  }

  onSearchInput(section: AssignmentSection): void {
    const state = this.getAssignmentState(section);
    state.errorMessage = '';
    state.successMessage = '';
    state.selectedUser = null;
    state.candidate = null;
    if (this.searchTimers[section]) {
      clearTimeout(this.searchTimers[section]);
    }
    if (!state.organizationId || state.searchText.trim().length < 3) {
      state.searchResults = [];
      this.cdr.markForCheck();
      return;
    }
    this.searchTimers[section] = setTimeout(() => this.searchCustomers(section), 250);
  }

  selectUser(section: AssignmentSection, user: UserSearchResult): void {
    const state = this.getAssignmentState(section);
    state.selectedUser = user;
    state.searchResults = [];
    state.searchText = user.name || user.email || '';
    state.successMessage = '';
    state.errorMessage = '';
    this.loadAssignments(section, user.id);
  }

  isBranchSelected(state: AssignmentState, branchId: number): boolean {
    return state.selectedBranchIds.includes(branchId);
  }

  onBranchToggle(state: AssignmentState, branchId: number, checked: boolean): void {
    if (checked) {
      if (!state.selectedBranchIds.includes(branchId)) {
        state.selectedBranchIds = [...state.selectedBranchIds, branchId];
      }
    } else {
      state.selectedBranchIds = state.selectedBranchIds.filter((id) => id !== branchId);
      if (state.baseBranchId === branchId) {
        state.baseBranchId = null;
      }
    }
  }

  saveAssignment(section: AssignmentSection): void {
    const actorEmail = this.getActorEmail();
    const state = this.getAssignmentState(section);
    if (!actorEmail || !state.selectedUser || !state.organizationId || !state.role || !state.baseBranchId || state.selectedBranchIds.length === 0) {
      return;
    }

    state.isSaving = true;
    state.errorMessage = '';
    state.successMessage = '';
    this.cdr.markForCheck();

    this.http.post('/api/super-admin/staff-assignment', {
      actorEmail,
      userId: state.selectedUser.id,
      organizationId: state.organizationId,
      role: state.role,
      baseBranchId: state.baseBranchId,
      branchIds: state.selectedBranchIds,
    }, {
      headers: this.buildActorHeaders(),
    }).subscribe({
      next: () => {
        state.isSaving = false;
        state.successMessage = 'Staff access updated successfully';
        this.loadAssignments(section, state.selectedUser!.id);
      },
      error: (err) => {
        state.isSaving = false;
        state.errorMessage = err?.error?.message || 'Unable to update staff access right now';
        this.cdr.markForCheck();
      },
    });
  }

  trackById(index: number, item: { id: number }): number {
    return item.id;
  }

  protected formatOrganizationLocation(organization: OrganizationAdminRow): string {
    return [organization.city, organization.state, organization.country].filter(Boolean).join(', ');
  }

  protected formatBranchLocation(branch: BranchAdminRow): string {
    return [branch.city, branch.state].filter(Boolean).join(', ');
  }

  protected getMembershipForSelectedOrganization(state: AssignmentState): MembershipSummary | null {
    if (!state.candidate || !state.organizationId) {
      return null;
    }
    return (
      state.candidate.memberships.find((membership) => membership.organizationId === state.organizationId) ??
      null
    );
  }

  private loadPortal(): void {
    const actorEmail = this.getActorEmail();
    if (!actorEmail) {
      this.portalError = 'Authenticated actor email is missing';
      return;
    }

    this.isLoadingPortal = true;
    this.portalError = '';
    this.cdr.markForCheck();

    this.http
      .get<PortalContextResponse>(`/api/super-admin/context?email=${encodeURIComponent(actorEmail)}`, {
        headers: this.buildActorHeaders(),
      })
      .subscribe({
        next: (response) => {
          this.isLoadingPortal = false;
          this.activeOrganizations = response.organizations ?? [];
          this.assignableRoles = response.assignableRoles ?? [];
          this.selectedBranchesOrganizationId = this.activeOrganizations[0]?.id ?? null;
          this.branchForm.organizationId = this.selectedBranchesOrganizationId;
          this.grantState.organizationId = this.activeOrganizations[0]?.id ?? null;
          this.onboardState.organizationId = this.activeOrganizations[0]?.id ?? null;
          this.loadOrganizations();
          if (this.selectedBranchesOrganizationId) {
            this.loadBranchesForOrganization(this.selectedBranchesOrganizationId);
          }
          if (this.grantState.organizationId) {
            this.loadAssignmentBranches('grant', this.grantState.organizationId);
          }
          if (this.onboardState.organizationId) {
            this.loadAssignmentBranches('onboard', this.onboardState.organizationId);
          }
          this.cdr.markForCheck();
        },
        error: (err) => {
          this.isLoadingPortal = false;
          this.portalError = err?.error?.message || 'Unable to load Super Admin Portal right now';
          if (err?.status === 403) {
            void this.router.navigate(['/dashboard']);
          }
          this.cdr.markForCheck();
        },
      });
  }

  private loadOrganizations(): void {
    const actorEmail = this.getActorEmail();
    if (!actorEmail) {
      return;
    }

    this.isLoadingOrganizations = true;
    this.cdr.markForCheck();

    this.http
      .get<OrganizationAdminRow[]>(`/api/super-admin/organizations?email=${encodeURIComponent(actorEmail)}`, {
        headers: this.buildActorHeaders(),
      })
      .subscribe({
        next: (organizations) => {
          this.organizations = organizations ?? [];
          this.isLoadingOrganizations = false;
          this.cdr.markForCheck();
        },
        error: (err) => {
          this.organizations = [];
          this.isLoadingOrganizations = false;
          this.portalError = err?.error?.message || 'Unable to load organizations right now';
          this.cdr.markForCheck();
        },
      });
  }

  private loadBranchesForOrganization(organizationId: number): void {
    const actorEmail = this.getActorEmail();
    if (!actorEmail) {
      return;
    }

    this.isLoadingBranches = true;
    this.branchError = '';
    this.cdr.markForCheck();

    this.http
      .get<BranchAdminRow[]>(
        `/api/super-admin/branches?email=${encodeURIComponent(actorEmail)}&organizationId=${organizationId}`,
        { headers: this.buildActorHeaders() },
      )
      .subscribe({
        next: (branches) => {
          this.branches = branches ?? [];
          this.isLoadingBranches = false;
          this.cdr.markForCheck();
        },
        error: (err) => {
          this.branches = [];
          this.isLoadingBranches = false;
          this.branchError = err?.error?.message || 'Unable to load branches right now';
          this.cdr.markForCheck();
        },
      });
  }

  private loadAssignmentBranches(section: AssignmentSection, organizationId: number, membership?: MembershipSummary | null): void {
    const state = this.getAssignmentState(section);
    const actorEmail = this.getActorEmail();
    if (!actorEmail) {
      return;
    }

    state.isLoadingBranches = true;
    this.cdr.markForCheck();

    this.http
      .get<BranchAdminRow[]>(
        `/api/super-admin/branches?email=${encodeURIComponent(actorEmail)}&organizationId=${organizationId}`,
        { headers: this.buildActorHeaders() },
      )
      .subscribe({
        next: (branches) => {
          state.branches = (branches ?? []).filter((branch) => branch.active);
          state.isLoadingBranches = false;
          this.applyMembershipToState(state, membership ?? this.getMembershipForSelectedOrganization(state));
          this.cdr.markForCheck();
        },
        error: (err) => {
          state.branches = [];
          state.isLoadingBranches = false;
          state.errorMessage = err?.error?.message || 'Unable to load organization branches right now';
          this.cdr.markForCheck();
        },
      });
  }

  private searchCustomers(section: AssignmentSection): void {
    const actorEmail = this.getActorEmail();
    const state = this.getAssignmentState(section);
    if (!actorEmail || !state.organizationId || state.searchText.trim().length < 3) {
      return;
    }

    state.isSearching = true;
    this.cdr.markForCheck();

    this.http
      .get<UserSearchResult[]>(
        `/api/super-admin/users/search?email=${encodeURIComponent(actorEmail)}&organizationId=${state.organizationId}&query=${encodeURIComponent(state.searchText.trim())}`,
        { headers: this.buildActorHeaders() },
      )
      .subscribe({
        next: (results) => {
          state.searchResults = results ?? [];
          state.isSearching = false;
          this.cdr.markForCheck();
        },
        error: (err) => {
          state.searchResults = [];
          state.isSearching = false;
          state.errorMessage = err?.error?.message || 'Unable to search users right now';
          this.cdr.markForCheck();
        },
      });
  }

  private loadAssignments(section: AssignmentSection, userId: number): void {
    const actorEmail = this.getActorEmail();
    const state = this.getAssignmentState(section);
    if (!actorEmail) {
      return;
    }

    state.errorMessage = '';
    state.successMessage = '';
    this.cdr.markForCheck();

    this.http
      .get<CandidateDetails>(`/api/super-admin/users/${userId}/assignments?email=${encodeURIComponent(actorEmail)}`, {
        headers: this.buildActorHeaders(),
      })
      .subscribe({
        next: (candidate) => {
          state.candidate = candidate;
          const existingMembership = this.getMembershipForSelectedOrganization(state);
          if (state.organizationId) {
            this.loadAssignmentBranches(section, state.organizationId, existingMembership);
          } else {
            this.applyMembershipToState(state, existingMembership);
            this.cdr.markForCheck();
          }
        },
        error: (err) => {
          state.candidate = null;
          state.errorMessage = err?.error?.message || 'Unable to load user assignments right now';
          this.cdr.markForCheck();
        },
      });
  }

  private applyMembershipToState(state: AssignmentState, membership: MembershipSummary | null): void {
    if (!membership) {
      state.role = null;
      state.baseBranchId = null;
      state.selectedBranchIds = [];
      return;
    }

    state.role = membership.role ?? null;
    state.baseBranchId = membership.baseBranchId ?? null;
    const accessibleBranchIds = membership.accessibleBranches?.map((branch) => branch.id) ?? [];
    state.selectedBranchIds = Array.from(new Set(accessibleBranchIds));
  }

  private getAssignmentState(section: AssignmentSection): AssignmentState {
    return section === 'grant' ? this.grantState : this.onboardState;
  }

  private resetOrganizationForm(): void {
    this.organizationFormExpanded = false;
    this.editingOrganizationId = null;
    this.organizationForm = this.createEmptyOrganizationForm();
    this.cdr.markForCheck();
  }

  private resetBranchForm(): void {
    this.branchFormExpanded = false;
    this.editingBranchId = null;
    this.branchForm = {
      ...this.createEmptyBranchForm(),
      organizationId: this.selectedBranchesOrganizationId,
    };
    this.cdr.markForCheck();
  }

  private createEmptyOrganizationForm(): OrganizationFormState {
    return {
      name: '',
      logoUrl: '',
      phone: '',
      email: '',
      address: '',
      city: '',
      state: '',
      country: '',
    };
  }

  private createEmptyBranchForm(): BranchFormState {
    return {
      organizationId: null,
      name: '',
      branchCode: '',
      address: '',
      city: '',
      state: '',
      phone: '',
      email: '',
      latitude: '',
      longitude: '',
      isActive: true,
    };
  }

  private createAssignmentState(): AssignmentState {
    return {
      organizationId: null,
      searchText: '',
      searchResults: [],
      isSearching: false,
      selectedUser: null,
      candidate: null,
      role: null,
      baseBranchId: null,
      selectedBranchIds: [],
      branches: [],
      isLoadingBranches: false,
      isSaving: false,
      successMessage: '',
      errorMessage: '',
    };
  }

  private parseDecimal(value: string): number | null {
    const normalized = value.trim();
    if (!normalized) {
      return null;
    }
    const parsed = Number(normalized);
    return Number.isFinite(parsed) ? parsed : null;
  }

  private buildActorHeaders(): HttpHeaders {
    const actorEmail = this.getActorEmail();
    return actorEmail ? new HttpHeaders({ 'X-User-Email': actorEmail }) : new HttpHeaders();
  }

  private getActorEmail(): string | null {
    return this.auth.getSnapshot()?.user.email?.trim() ?? null;
  }
}
