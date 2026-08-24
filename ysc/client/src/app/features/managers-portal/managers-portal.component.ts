import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ChangeDetectionStrategy, ChangeDetectorRef, } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { OrganizationContextService } from '../../core/services/organization-context.service';
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
  userId?: number;
  name: string;
  due: number | string | null;
}

interface PendingFrameBreakdown {
  frameId: number;
  matchup: string;
  date: string;
  dueAmount: number | string | null;
}

interface PendingConsumableBreakdown {
  orderId: number;
  itemName: string;
  quantity: number;
  price: number | string | null;
  totalCost: number | string | null;
  createdAt: string;
}

interface PendingKidsPlayBreakdown {
  sessionId: number;
  childName: string;
  date: string;
  amount: number | string | null;
}

interface PendingDueBreakdown {
  frames: PendingFrameBreakdown[];
  consumables: PendingConsumableBreakdown[];
  kidsPlay: PendingKidsPlayBreakdown[];
  frameDue: number | string | null;
  consumableDue: number | string | null;
  kidsDue: number | string | null;
  totalDue: number | string | null;
}

interface TodayEarnings {
  totalEarnings: number | string | null;
  totalDue: number | string | null;
  duePlayers: DuePlayer[];
  settledPayments: SettledPayment[];
}

interface SettledPayment {
  userName: string;
  paidAmount: number | string | null;
  discount: number | string | null;
  date: string;
  paymentMethod?: string | null;
}

interface MessageResponse {
  message: string;
}

interface AddCustomerResponse extends MessageResponse {
  userId?: number;
  customerName?: string;
  phone?: string;
  organizationId?: number;
  organizationName?: string;
  organizationUserId?: number;
  membershipCreated?: boolean;
  membershipReactivated?: boolean;
  baseBranchId?: number | null;
  baseBranchName?: string | null;
  branchAccessCreated?: boolean;
  branchAccessReactivated?: boolean;
}

interface CustomerSearchResult {
  id: number;
  name: string;
  email: string;
  phone: string | null;
  googleId: string | null;
  role?: string | null;
}

interface ChildProfile {
  id: number;
  name: string;
  dateOfBirth: string | null;
  address?: string | null;
  school?: string | null;
}

interface BranchExpense {
  id: number;
  expenseName: string;
  amount: number | string | null;
  expenseType: 'COUNTER_CASH' | 'POCKET_CASH';
  expenseDate: string;
  notes?: string | null;
  paidByUserId: number;
  paidByName: string;
  createdByUserId: number;
  createdByName: string;
  branchId: number;
  branchName: string;
  createdAt: string;
}

interface ExpensePayerOption {
  userId: number;
  name: string;
  role: string | null;
}

interface ExpenseMonthOption {
  value: string;
  label: string;
  year: number;
  month: number;
}

interface OnboardingBranchOption {
  id: number;
  name: string;
}

interface OnboardingOrganizationOption {
  id: number;
  name: string;
}

interface CustomerMembershipSummary {
  organizationId: number | null;
  organizationName: string | null;
  role: string | null;
  active: boolean;
  baseBranchId: number | null;
  baseBranchName: string | null;
  accessibleBranches: OnboardingBranchOption[];
}

interface CustomerOnboardingCandidate {
  userId: number;
  name: string;
  email: string | null;
  phone: string | null;
  memberships: CustomerMembershipSummary[];
}

interface CustomerOnboardingContext {
  actorRole: string | null;
  organizationSelectable: boolean;
  multipleBranchSelectionAllowed: boolean;
  currentOrganizationId: number | null;
  currentOrganizationName: string;
  currentBranchId: number | null;
  currentBranchName: string | null;
  organizations: OnboardingOrganizationOption[];
  branches: OnboardingBranchOption[];
}

interface CustomerOnboardingResponse {
  userId: number;
  customerName: string;
  organizationId: number;
  organizationName: string;
  organizationUserId: number;
  membershipCreated: boolean;
  membershipReactivated: boolean;
  baseBranchId: number | null;
  baseBranchName: string | null;
  branchesAdded: OnboardingBranchOption[];
  alreadyAccessibleBranches: OnboardingBranchOption[];
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
  private readonly authService = inject(AuthService);
  private readonly organizationContextService = inject(OrganizationContextService);
  private readonly changeDetectorRef = inject(ChangeDetectorRef);
  private readonly today = new Date();
  currentOrganizationName: string = '';
  private resizeHandler: (() => void) | null = null;
  private onboardingSearchTimeoutId: number | null = null;
  private readonly subscriptions = new Subscription();
  private currentBranchId: number | null = null;
  private playersRequestVersion = 0;
  private branchStateVersion = 0;
  private expensesRequestVersion = 0;
  private expensePayersRequestVersion = 0;

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
  selectedEarningsDate = '';
  minEarningsDate = '';
  maxEarningsDate = '';
  todayEarnings: TodayEarnings = {
    totalEarnings: 0,
    totalDue: 0,
    duePlayers: [],
    settledPayments: [],
  };
  isAddCustomerExpanded = false;
  isSavingCustomer = false;
  customerForm = {
    name: '',
    email: '',
    mobileNumber: '',
  };
  addCustomerOrganizationName = '';
  addCustomerBranchName = '';
  isAddChildExpanded = false;
  isSearchingChildParent = false;
  isSavingChild = false;
  isLoadingParentChildren = false;
  childParentSearch = '';
  childParentResults: CustomerSearchResult[] = [];
  selectedChildParent: CustomerSearchResult | null = null;
  parentChildren: ChildProfile[] = [];
  childForm = {
    name: '',
    dateOfBirth: '',
  };
  isUpdateCustomerExpanded = false;
  isSearchingUpdateCustomers = false;
  isUpdatingCustomer = false;
  updateCustomerSearch = '';
  updateCustomerResults: CustomerSearchResult[] = [];
  selectedUpdateCustomer: CustomerSearchResult | null = null;
  updateCustomerForm = {
    userId: null as number | null,
    name: '',
    email: '',
    phone: '',
  };
  isOnboardExistingExpanded = false;
  isLoadingOnboardingContext = false;
  isSearchingOnboardingCustomers = false;
  isLoadingOnboardingCustomer = false;
  isSavingOnboardingCustomer = false;
  onboardingSearch = '';
  onboardingSearchResults: CustomerSearchResult[] = [];
  selectedOnboardingCandidate: CustomerOnboardingCandidate | null = null;
  onboardingContext: CustomerOnboardingContext | null = null;
  selectedOnboardingOrganizationId: number | null = null;
  selectedOnboardingBranchIds: number[] = [];
  selectedOnboardingBaseBranchId: number | null = null;
  isMonthlyExpensesExpanded = false;
  isLoadingMonthlyExpenses = false;
  hasLoadedMonthlyExpenses = false;
  monthlyExpenses: BranchExpense[] = [];
  expenseMonthOptions: ExpenseMonthOption[] = [];
  selectedExpenseMonthValue = '';
  isAddExpenseExpanded = false;
  isLoadingExpensePayers = false;
  expensePayers: ExpensePayerOption[] = [];
  isSavingExpense = false;
  expenseForm = {
    expenseName: '',
    amount: null as number | null,
    expenseType: '',
    paidByUserId: null as number | null,
    expenseDate: '',
    notes: '',
  };

  isPlayersExpanded = false;
  isLoadingPlayers = false;
  players: PlayerSummary[] = [];
  playersPage = 0;
  hasMorePlayers = true;

  showSettlementPopup = false;
  settlementPlayer: DuePlayer | null = null;
  settlementTotalDue = 0;
  settlementFrameDue = 0;
  settlementConsumableDue = 0;
  settlementKidsDue = 0;
  
  settleAmount: number | null = null;
  discountAmount: number | null = null;
  paymentMode = '';
  isSavingSettlement = false;
  isLoadingSettlementDetails = false;
  showItemsPopup = false;
  itemsPlayer: DuePlayer | null = null;
  isLoadingItemsBreakdown = false;
  dueBreakdown: PendingDueBreakdown = {
    frames: [],
    consumables: [],
    kidsPlay: [],
    frameDue: 0,
    consumableDue: 0,
    kidsDue: 0,
    totalDue: 0,
  };

  ngOnInit(): void {
    this.updateViewportState();
    this.maxCompletedDate = this.formatDate(this.today);
    this.selectedCompletedDate = this.maxCompletedDate;
    const minDate = new Date(this.today);
    minDate.setDate(minDate.getDate() - 60);
    this.minCompletedDate = this.formatDate(minDate);
    this.maxEarningsDate = this.maxCompletedDate;
    this.selectedEarningsDate = this.maxEarningsDate;
    this.minEarningsDate = this.minCompletedDate;
    this.initializeExpenseMonthOptions();
    this.resetExpenseForm();
    this.resizeHandler = () => this.updateViewportState();
    window.addEventListener('resize', this.resizeHandler);
    this.bindOrganizationContext();
    this.loadViewerAccess();

    this.organizationContextService.context$.subscribe((context) => {
      this.currentOrganizationName = context?.currentOrganization?.name ?? '';
      this.changeDetectorRef.markForCheck();
    });
  }

  ngOnDestroy(): void {
    if (this.resizeHandler) {
      window.removeEventListener('resize', this.resizeHandler);
      this.resizeHandler = null;
    }
    if (this.onboardingSearchTimeoutId !== null) {
      window.clearTimeout(this.onboardingSearchTimeoutId);
      this.onboardingSearchTimeoutId = null;
    }
    this.subscriptions.unsubscribe();
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
      this.loadEarningsForSelectedDate();
    }
  }

  loadTodayEarnings(): void {
    this.isLoadingEarnings = true;
    const headers = this.buildActorHeaders();
    const requestVersion = this.branchStateVersion;

    this.http.get<TodayEarnings>('/api/analytics/today-earnings', { headers }).subscribe({
      next: (earnings) => {
        if (requestVersion !== this.branchStateVersion) {
          return;
        }
        this.todayEarnings = {
          totalEarnings: earnings?.totalEarnings ?? 0,
          totalDue: earnings?.totalDue ?? 0,
          duePlayers: earnings?.duePlayers ?? [],
          settledPayments: earnings?.settledPayments ?? [],
        };
        this.hasLoadedEarnings = true;
        this.isLoadingEarnings = false;
      },
      error: (err) => {
        if (requestVersion !== this.branchStateVersion) {
          return;
        }
        console.error('Failed to load today earnings', err);
        this.todayEarnings = {
          totalEarnings: 0,
          totalDue: 0,
          duePlayers: [],
          settledPayments: [],
        };
        this.hasLoadedEarnings = false;
        this.isLoadingEarnings = false;
      },
    });
  }

  loadEarningsForSelectedDate(): void {
    if (this.selectedEarningsDate === this.maxEarningsDate) {
      this.loadTodayEarnings();
      return;
    }

    this.isLoadingEarnings = true;
    const headers = this.buildActorHeaders();
    const requestVersion = this.branchStateVersion;

    this.http.get<TodayEarnings>(`/api/manager/earnings?date=${this.selectedEarningsDate}`, { headers }).subscribe({
      next: (earnings) => {
        if (requestVersion !== this.branchStateVersion) {
          return;
        }
        this.todayEarnings = {
          totalEarnings: earnings?.totalEarnings ?? 0,
          totalDue: earnings?.totalDue ?? 0,
          duePlayers: earnings?.duePlayers ?? [],
          settledPayments: earnings?.settledPayments ?? [],
        };
        this.hasLoadedEarnings = true;
        this.isLoadingEarnings = false;
      },
      error: (err) => {
        if (requestVersion !== this.branchStateVersion) {
          return;
        }
        console.error('Failed to load earnings', err);
        this.todayEarnings = {
          totalEarnings: 0,
          totalDue: 0,
          duePlayers: [],
          settledPayments: [],
        };
        this.hasLoadedEarnings = false;
        this.isLoadingEarnings = false;
        alert(err?.error?.message || 'Unable to load earnings right now');
      },
    });
  }

  onEarningsDateChange(): void {
    if (!this.selectedEarningsDate) {
      return;
    }

    if (this.selectedEarningsDate < this.minEarningsDate || this.selectedEarningsDate > this.maxEarningsDate) {
      alert('Please select a valid date within the last 60 days');
      this.selectedEarningsDate = this.maxEarningsDate;
      return;
    }

    if (!this.isEarningsExpanded) {
      this.isEarningsExpanded = true;
    }

    this.loadEarningsForSelectedDate();
  }

  loadOngoingFrames(): void {
    this.isLoadingOngoing = true;
    const headers = this.buildActorHeaders();
    const requestVersion = this.branchStateVersion;

    this.http.get<OngoingFrame[]>('/api/frame/ongoing/today', { headers }).subscribe({
      next: (frames) => {
        if (requestVersion !== this.branchStateVersion) {
          return;
        }
        this.ongoingFrames = frames;
        this.isLoadingOngoing = false;
      },
      error: (err) => {
        if (requestVersion !== this.branchStateVersion) {
          return;
        }
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
    const headers = this.buildActorHeaders();
    const requestVersion = this.branchStateVersion;

    const request$ = useTodayApi
      ? this.http.get<CompletedFrame[]>('/api/frame/completed/today', { headers })
      : this.http.get<CompletedFrame[]>(`/api/frame/completed?date=${this.selectedCompletedDate}`, { headers });

    request$.subscribe({
      next: (frames) => {
        if (requestVersion !== this.branchStateVersion) {
          return;
        }
        this.completedFrames = frames;
        this.isLoadingCompleted = false;
      },
      error: (err) => {
        if (requestVersion !== this.branchStateVersion) {
          return;
        }
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
    if (this.isAddCustomerExpanded && (!this.addCustomerOrganizationName || !this.addCustomerBranchName)) {
      this.refreshAddCustomerContext();
    }
  }

  toggleAddChild(): void {
    this.isAddChildExpanded = !this.isAddChildExpanded;
  }

  toggleUpdateCustomer(): void {
    this.isUpdateCustomerExpanded = !this.isUpdateCustomerExpanded;
  }

  toggleMonthlyExpenses(): void {
    this.isMonthlyExpensesExpanded = !this.isMonthlyExpensesExpanded;

    if (this.isMonthlyExpensesExpanded) {
      if (!this.hasLoadedMonthlyExpenses && !this.isLoadingMonthlyExpenses) {
        this.loadMonthlyExpenses();
      }
      if (this.expensePayers.length === 0 && !this.isLoadingExpensePayers) {
        this.loadExpensePayers();
      }
    }
  }

  toggleAddExpensePanel(): void {
    this.isAddExpenseExpanded = !this.isAddExpenseExpanded;

    if (this.isAddExpenseExpanded && this.expensePayers.length === 0 && !this.isLoadingExpensePayers) {
      this.loadExpensePayers();
    }
  }

  toggleOnboardExistingUser(): void {
    this.isOnboardExistingExpanded = !this.isOnboardExistingExpanded;

    if (this.isOnboardExistingExpanded && !this.isLoadingOnboardingContext && !this.onboardingContext) {
      this.loadOnboardingContext();
    }
  }

  onCustomerMobileInput(event: Event): void {
    const inputElement = event.target as HTMLInputElement;
    const sanitized = inputElement.value.replace(/[^0-9]/g, '').slice(0, 10);
    if (inputElement.value !== sanitized) {
      inputElement.value = sanitized;
    }
    this.customerForm.mobileNumber = sanitized;
  }

  onChildParentSearchInput(): void {
    const query = this.childParentSearch.trim();

    if (query.length < 3) {
      this.isSearchingChildParent = false;
      this.childParentResults = [];
      return;
    }

    this.isSearchingChildParent = true;
    this.http.get<CustomerSearchResult[]>(`/api/users/search?query=${encodeURIComponent(query)}`).subscribe({
      next: (users) => {
        this.childParentResults = this.mapSearchResults(users, query);
        this.isSearchingChildParent = false;
      },
      error: (err) => {
        console.error('Failed to search parents', err);
        this.childParentResults = [];
        this.isSearchingChildParent = false;
      },
    });
  }

  selectChildParent(user: CustomerSearchResult): void {
    this.selectedChildParent = user;
    this.childParentSearch = user.name ?? '';
    this.childParentResults = [];
    this.loadParentChildren(user.id);
  }

  clearSelectedChildParent(): void {
    this.selectedChildParent = null;
    this.childParentSearch = '';
    this.childParentResults = [];
    this.parentChildren = [];
    this.resetChildForm();
  }

  isChildFormValid(): boolean {
    return !!this.selectedChildParent
      && this.childForm.name.trim().length > 0
      && !this.isSavingChild;
  }

  saveChild(): void {
    if (!this.selectedChildParent) {
      alert('Please select a parent first');
      return;
    }

    if (!this.childForm.name.trim()) {
      alert('Child name is required');
      return;
    }

    this.isSavingChild = true;
    this.http.post<ChildProfile>('/api/children', {
      parentUserId: this.selectedChildParent.id,
      name: this.childForm.name.trim(),
      dateOfBirth: this.childForm.dateOfBirth || null,
      address: '',
      school: '',
    }).subscribe({
      next: () => {
        this.isSavingChild = false;
        alert('Child added successfully');
        this.resetChildForm();
        this.loadParentChildren(this.selectedChildParent!.id);
      },
      error: (err) => {
        console.error('Failed to add child', err);
        this.isSavingChild = false;
        alert(err?.error?.message || 'Unable to add child right now');
      },
    });
  }

  onUpdateCustomerSearchInput(): void {
    const query = this.updateCustomerSearch.trim();

    if (query.length < 3) {
      this.isSearchingUpdateCustomers = false;
      this.updateCustomerResults = [];
      if (!query) {
        this.clearSelectedUpdateCustomer();
      }
      return;
    }

    this.isSearchingUpdateCustomers = true;
    this.http.get<CustomerSearchResult[]>(`/api/users/search?query=${encodeURIComponent(query)}`).subscribe({
      next: (users) => {
        this.updateCustomerResults = this.mapSearchResults(users, query);
        this.isSearchingUpdateCustomers = false;
      },
      error: (err) => {
        console.error('Failed to search customers', err);
        this.updateCustomerResults = [];
        this.isSearchingUpdateCustomers = false;
      },
    });
  }

  selectUpdateCustomer(user: CustomerSearchResult): void {
    this.selectedUpdateCustomer = user;
    this.updateCustomerSearch = user.name ?? '';
    this.updateCustomerResults = [];
    this.updateCustomerForm = {
      userId: user.id,
      name: user.name ?? '',
      email: user.email ?? '',
      phone: user.phone ?? '',
    };
  }

  onUpdateCustomerPhoneInput(event: Event): void {
    const inputElement = event.target as HTMLInputElement;
    const sanitized = inputElement.value.replace(/[^0-9]/g, '').slice(0, 10);
    if (inputElement.value !== sanitized) {
      inputElement.value = sanitized;
    }
    this.updateCustomerForm.phone = sanitized;
  }

  onOnboardingSearchInput(): void {
    const query = this.onboardingSearch.trim();

    if (this.onboardingSearchTimeoutId !== null) {
      window.clearTimeout(this.onboardingSearchTimeoutId);
      this.onboardingSearchTimeoutId = null;
    }

    if (query.length < 3) {
      this.isSearchingOnboardingCustomers = false;
      this.onboardingSearchResults = [];
      if (!query) {
        this.clearSelectedOnboardingCandidate();
      }
      return;
    }

    this.isSearchingOnboardingCustomers = true;
    this.onboardingSearchTimeoutId = window.setTimeout(() => {
      this.http.get<CustomerSearchResult[]>(`/api/users/search?query=${encodeURIComponent(query)}`).subscribe({
        next: (users) => {
          this.onboardingSearchResults = this.mapSearchResults(users, query, true);
          this.isSearchingOnboardingCustomers = false;
        },
        error: (err) => {
          console.error('Failed to search onboarding customers', err);
          this.onboardingSearchResults = [];
          this.isSearchingOnboardingCustomers = false;
        },
      });
      this.onboardingSearchTimeoutId = null;
    }, 250);
  }

  selectOnboardingCandidate(user: CustomerSearchResult): void {
    this.isLoadingOnboardingCustomer = true;
    this.selectedOnboardingCandidate = null;
    this.onboardingSearch = this.buildOnboardingSearchLabel(user);
    this.onboardingSearchResults = [];

    this.http.get<CustomerOnboardingCandidate>(`/api/manager/customer-onboarding/customer?userId=${user.id}`).subscribe({
      next: (candidate) => {
        this.selectedOnboardingCandidate = candidate;
        this.isLoadingOnboardingCustomer = false;
        this.syncOnboardingSelectionWithMembership();
      },
      error: (err) => {
        console.error('Failed to load onboarding candidate', err);
        this.isLoadingOnboardingCustomer = false;
        this.selectedOnboardingCandidate = null;
        alert(err?.error?.message || 'Unable to load customer membership details right now');
      },
    });
  }

  clearSelectedOnboardingCandidate(): void {
    this.selectedOnboardingCandidate = null;
    this.onboardingSearch = '';
    this.onboardingSearchResults = [];
    this.selectedOnboardingBranchIds = [];
    this.selectedOnboardingBaseBranchId = null;
  }

  onOnboardingOrganizationChange(): void {
    if (!this.selectedOnboardingOrganizationId) {
      this.selectedOnboardingBranchIds = [];
      this.selectedOnboardingBaseBranchId = null;
      return;
    }
    this.loadOnboardingContext(this.selectedOnboardingOrganizationId);
  }

  toggleOnboardingBranch(branchId: number): void {
    if (!this.onboardingContext) {
      return;
    }

    if (this.isExistingMembershipBranch(branchId)) {
      return;
    }

    if (!this.onboardingContext.multipleBranchSelectionAllowed) {
      this.selectedOnboardingBranchIds = [branchId];
      this.syncOnboardingBaseBranchSelection();
      return;
    }

    if (this.selectedOnboardingBranchIds.includes(branchId)) {
      this.selectedOnboardingBranchIds = this.selectedOnboardingBranchIds.filter((id) => id !== branchId);
    } else {
      this.selectedOnboardingBranchIds = [...this.selectedOnboardingBranchIds, branchId];
    }

    this.syncOnboardingBaseBranchSelection();
  }

  removeOnboardingBranch(branchId: number): void {
    if (this.isExistingMembershipBranch(branchId)) {
      return;
    }
    this.selectedOnboardingBranchIds = this.selectedOnboardingBranchIds.filter((id) => id !== branchId);
    this.syncOnboardingBaseBranchSelection();
  }

  onOnboardingBaseBranchChange(): void {
    if (
      this.selectedOnboardingBaseBranchId !== null
      && !this.selectedOnboardingBranchIds.includes(this.selectedOnboardingBaseBranchId)
    ) {
      this.selectedOnboardingBaseBranchId = null;
    }
  }

  canSubmitOnboarding(): boolean {
    if (this.isSavingOnboardingCustomer || this.isLoadingOnboardingContext || this.isLoadingOnboardingCustomer) {
      return false;
    }

    if (!this.selectedOnboardingCandidate || !this.selectedOnboardingOrganizationId) {
      return false;
    }

    if (this.selectedOnboardingBranchIds.length === 0) {
      return false;
    }

    if (!this.hasExistingMembershipForSelectedOrganization() && !this.selectedOnboardingBaseBranchId) {
      return false;
    }

    return true;
  }

  onboardExistingCustomer(): void {
    if (!this.canSubmitOnboarding() || !this.selectedOnboardingCandidate || !this.selectedOnboardingOrganizationId) {
      return;
    }

    const organizationName = this.getSelectedOnboardingOrganizationName();
    const branchNames = this.getSelectedOnboardingBranches().map((branch) => branch.name);
    const baseBranchName = this.getSelectedOnboardingBaseBranchName();
    const confirmationMessage = [
      `Onboard ${this.selectedOnboardingCandidate.name} to ${organizationName || 'the selected organization'}?`,
      '',
      'Branches:',
      ...branchNames.map((name) => `- ${name}`),
      '',
      `Base Branch: ${baseBranchName || 'Not selected'}`,
    ].join('\n');

    if (!confirm(confirmationMessage)) {
      return;
    }

    const actorEmail = this.getStoredUserEmail();
    if (!actorEmail) {
      alert('Unable to determine the logged-in user');
      return;
    }

    this.isSavingOnboardingCustomer = true;
    this.http.post<CustomerOnboardingResponse>('/api/manager/customer-onboarding', {
      actorEmail,
      userId: this.selectedOnboardingCandidate.userId,
      organizationId: this.selectedOnboardingOrganizationId,
      branchIds: this.selectedOnboardingBranchIds,
      baseBranchId: this.hasExistingMembershipForSelectedOrganization() ? null : this.selectedOnboardingBaseBranchId,
    }).subscribe({
      next: (response) => {
        this.isSavingOnboardingCustomer = false;
        const addedBranchNames = [
          ...response.branchesAdded.map((branch) => branch.name),
          ...response.alreadyAccessibleBranches.map((branch) => branch.name),
        ];
        alert(
          `${response.customerName} has been onboarded successfully.\n\nOrganization:\n${response.organizationName}\n\nBranches:\n${addedBranchNames.join(', ') || 'None'}\n\nBase Branch:\n${response.baseBranchName || '-'}`,
        );
        this.clearSelectedOnboardingCandidate();
        this.loadOnboardingContext(this.selectedOnboardingOrganizationId ?? undefined);
      },
      error: (err) => {
        console.error('Failed to onboard customer', err);
        this.isSavingOnboardingCustomer = false;
        alert(err?.error?.message || 'Unable to onboard customer right now');
      },
    });
  }

  isCustomerFormValid(): boolean {
    return this.customerForm.name.trim().length > 0
      && (!this.customerForm.email.trim() || this.isValidEmail(this.customerForm.email))
      && /^[0-9]{10}$/.test(this.customerForm.mobileNumber)
      && !this.isSavingCustomer;
  }

  hasCustomerFormMissingFields(): boolean {
    return this.customerForm.name.trim().length === 0
      || this.customerForm.mobileNumber.trim().length === 0;
  }

  hasCustomerEmailError(): boolean {
    return !this.hasCustomerFormMissingFields()
      && this.customerForm.email.trim().length > 0
      && !this.isValidEmail(this.customerForm.email);
  }

  hasCustomerMobileError(): boolean {
    return !this.hasCustomerFormMissingFields() && !/^[0-9]{10}$/.test(this.customerForm.mobileNumber);
  }

  isManualUpdateCustomer(): boolean {
    return (this.selectedUpdateCustomer?.googleId ?? '').startsWith('MANUAL_USER_');
  }

  isUpdateCustomerFormValid(): boolean {
    if (!this.selectedUpdateCustomer || this.updateCustomerForm.userId === null || this.isUpdatingCustomer) {
      return false;
    }

    const hasValidPhone = /^[0-9]{10}$/.test(this.updateCustomerForm.phone.trim());
    if (!hasValidPhone) {
      return false;
    }

    if (!this.isManualUpdateCustomer()) {
      return true;
    }

    return this.updateCustomerForm.name.trim().length > 0
      && (!this.updateCustomerForm.email.trim() || this.isValidEmail(this.updateCustomerForm.email));
  }

  hasUpdateCustomerMissingFields(): boolean {
    if (!this.selectedUpdateCustomer) {
      return false;
    }

    if (this.isManualUpdateCustomer() && this.updateCustomerForm.name.trim().length === 0) {
      return true;
    }

    return this.updateCustomerForm.phone.trim().length === 0;
  }

  hasUpdateCustomerEmailError(): boolean {
    return !!this.selectedUpdateCustomer
      && this.isManualUpdateCustomer()
      && !this.hasUpdateCustomerMissingFields()
      && this.updateCustomerForm.email.trim().length > 0
      && !this.isValidEmail(this.updateCustomerForm.email);
  }

  hasUpdateCustomerPhoneError(): boolean {
    return !!this.selectedUpdateCustomer
      && !this.hasUpdateCustomerMissingFields()
      && !/^[0-9]{10}$/.test(this.updateCustomerForm.phone);
  }

  saveCustomer(): void {
    if (!this.isCustomerFormValid()) {
      return;
    }

    this.isSavingCustomer = true;
    const actorEmail = this.authService.getSnapshot()?.user.email ?? this.getStoredUserEmail();
    if (!actorEmail) {
      this.isSavingCustomer = false;
      alert('Unable to determine the logged-in user');
      return;
    }

    this.http.post<AddCustomerResponse>('/api/users/create-customer', {
      name: this.customerForm.name.trim(),
      email: this.customerForm.email.trim().toLowerCase(),
      mobileNumber: this.customerForm.mobileNumber.trim(),
    }, {
      headers: new HttpHeaders({
        'X-User-Email': actorEmail,
      }),
    }).subscribe({
      next: (response) => {
        this.isSavingCustomer = false;
        const organizationName = response?.organizationName || this.addCustomerOrganizationName || '-';
        const branchName = response?.baseBranchName || this.addCustomerBranchName || '-';
        alert(`${response?.message || 'Customer added successfully'}\n\nOrganization:\n${organizationName}\n\nBase Branch:\n${branchName}`);
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

  updateCustomer(): void {
    if (!this.isUpdateCustomerFormValid() || this.updateCustomerForm.userId === null) {
      return;
    }

    this.isUpdatingCustomer = true;
    this.http.put<CustomerSearchResult>('/api/customer/update', {
      userId: this.updateCustomerForm.userId,
      name: this.updateCustomerForm.name.trim(),
      email: this.updateCustomerForm.email.trim().toLowerCase(),
      phone: this.updateCustomerForm.phone.trim(),
    }).subscribe({
      next: (user) => {
        this.isUpdatingCustomer = false;
        const updatedUser: CustomerSearchResult = {
          id: user.id,
          name: user.name ?? '',
          email: user.email ?? '',
          phone: user.phone ?? '',
          googleId: user.googleId ?? this.selectedUpdateCustomer?.googleId ?? null,
        };
        this.selectUpdateCustomer(updatedUser);
        alert('Customer updated successfully');
      },
      error: (err) => {
        console.error('Failed to update customer', err);
        this.isUpdatingCustomer = false;
        alert(err?.error?.message || 'Unable to update customer right now');
      },
    });
  }

  togglePlayers(): void {
    this.isPlayersExpanded = !this.isPlayersExpanded;

    if (this.isPlayersExpanded && this.players.length === 0) {
      this.loadPlayers();
    }
  }

  onExpenseMonthChange(): void {
    if (!this.isMonthlyExpensesExpanded || !this.selectedExpenseMonthValue) {
      return;
    }
    this.loadMonthlyExpenses();
  }

  isExpenseFormValid(): boolean {
    return this.expenseForm.expenseName.trim().length > 0
      && this.expenseForm.expenseName.trim().length <= 200
      && this.expenseForm.amount !== null
      && Number(this.expenseForm.amount) > 0
      && !!this.expenseForm.expenseType
      && this.expenseForm.paidByUserId !== null
      && !!this.expenseForm.expenseDate
      && this.expenseForm.expenseDate <= this.maxCompletedDate
      && !this.isSavingExpense;
  }

  saveExpense(): void {
    if (!this.isExpenseFormValid()) {
      return;
    }

    this.isSavingExpense = true;
    this.http.post<BranchExpense>('/api/manager/expenses', {
      expenseName: this.expenseForm.expenseName.trim(),
      amount: this.expenseForm.amount,
      expenseType: this.expenseForm.expenseType,
      paidByUserId: this.expenseForm.paidByUserId,
      expenseDate: this.expenseForm.expenseDate,
      notes: this.expenseForm.notes.trim(),
    }, { headers: this.buildActorHeaders() }).subscribe({
      next: (expense) => {
        this.isSavingExpense = false;
        const savedMonthValue = (expense?.expenseDate ?? '').slice(0, 7);
        const savedMonthLabel = this.getExpenseMonthLabel(savedMonthValue);
        this.resetExpenseForm();

        if (savedMonthValue && savedMonthValue === this.selectedExpenseMonthValue) {
          this.loadMonthlyExpenses();
          alert('Expense added successfully');
          return;
        }

        if (savedMonthValue && this.expenseMonthOptions.some((option) => option.value === savedMonthValue)) {
          this.selectedExpenseMonthValue = savedMonthValue;
          this.loadMonthlyExpenses();
          alert(`Expense added successfully for ${savedMonthLabel}.`);
          return;
        }

        alert(`Expense added successfully${savedMonthLabel ? ` for ${savedMonthLabel}` : ''}.`);
      },
      error: (err) => {
        console.error('Failed to save expense', err);
        this.isSavingExpense = false;
        alert(err?.error?.message || 'Unable to save expense right now');
      },
    });
  }

  loadPlayers(): void {
    if (this.isLoadingPlayers || !this.hasMorePlayers) return;
    const actorEmail = this.authService.getSnapshot()?.user.email ?? this.getStoredUserEmail();
    if (!actorEmail || !this.currentBranchId) {
      this.players = [];
      this.hasMorePlayers = false;
      this.isLoadingPlayers = false;
      return;
    }

    this.isLoadingPlayers = true;
    const requestVersion = this.playersRequestVersion;
    const headers = new HttpHeaders({
      'X-User-Email': actorEmail.trim(),
    });

    this.http.get<any>(`/api/users/player-summary?page=${this.playersPage}&size=20`, { headers }).subscribe({
      next: (response) => {
        if (requestVersion !== this.playersRequestVersion) {
          return;
        }
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
        if (requestVersion !== this.playersRequestVersion) {
          return;
        }
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
    const email = this.getStoredUserEmail();
    if (!email) {
      this.canViewTodayEarnings = false;
      return;
    }

    this.http.get<{ role?: string }>(`/api/user?email=${encodeURIComponent(email)}`).subscribe({
      next: (user) => {
        this.canViewTodayEarnings = ['MANAGER', 'ADMIN', 'SUPER_ADMIN'].includes(user?.role ?? '');
      },
      error: (err) => {
        console.error('Failed to load viewer role', err);
        this.canViewTodayEarnings = false;
      },
    });
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

  private bindOrganizationContext(): void {
    this.currentBranchId = this.organizationContextService.getSnapshot()?.currentBranch?.id ?? null;

    this.subscriptions.add(
      this.organizationContextService.context$.subscribe((context) => {
        this.addCustomerOrganizationName = context?.currentOrganization?.name ?? '';
        this.addCustomerBranchName = context?.currentBranch?.name ?? '';

        const nextBranchId = context?.currentBranch?.id ?? null;
        if (this.currentBranchId === nextBranchId) {
          return;
        }

        this.currentBranchId = nextBranchId;
        this.branchStateVersion++;
        this.resetFrameListsForBranchChange();
        this.resetEarningsForBranchChange();
        this.resetPlayersForBranchChange();
        this.resetMonthlyExpensesForBranchChange();
        if (nextBranchId) {
          if (this.isOngoingExpanded) {
            this.loadOngoingFrames();
          }
          if (this.isCompletedExpanded) {
            this.loadCompletedFrames(this.selectedCompletedDate === this.maxCompletedDate);
          }
          if (this.isEarningsExpanded) {
            this.loadEarningsForSelectedDate();
          }
          if (this.isPlayersExpanded) {
            this.loadPlayers();
          }
          if (this.isMonthlyExpensesExpanded) {
            this.loadMonthlyExpenses();
            this.loadExpensePayers();
          }
        }
      }),
    );

    const snapshot = this.organizationContextService.getSnapshot();
    if (snapshot?.currentOrganization?.name || snapshot?.currentBranch?.name) {
      this.addCustomerOrganizationName = snapshot?.currentOrganization?.name ?? '';
      this.addCustomerBranchName = snapshot?.currentBranch?.name ?? '';
      return;
    }

    this.refreshAddCustomerContext();
  }

  private refreshAddCustomerContext(): void {
    const actorEmail = this.authService.getSnapshot()?.user.email ?? this.getStoredUserEmail();
    if (!actorEmail) {
      return;
    }

    this.subscriptions.add(
      this.organizationContextService.loadContext(actorEmail).subscribe({
        error: (err) => {
          console.error('Failed to load add customer context', err);
        },
      }),
    );
  }

  private resetPlayersForBranchChange(): void {
    this.playersRequestVersion++;
    this.players = [];
    this.playersPage = 0;
    this.hasMorePlayers = true;
    this.isLoadingPlayers = false;
    this.showSettlementPopup = false;
    this.settlementPlayer = null;
    this.settlementTotalDue = 0;
    this.settlementFrameDue = 0;
    this.settlementConsumableDue = 0;
    this.settlementKidsDue = 0;
    this.settleAmount = null;
    this.discountAmount = null;
    this.paymentMode = '';
    this.showItemsPopup = false;
    this.itemsPlayer = null;
    this.isLoadingItemsBreakdown = false;
    this.dueBreakdown = {
      frames: [],
      consumables: [],
      kidsPlay: [],
      frameDue: 0,
      consumableDue: 0,
      kidsDue: 0,
      totalDue: 0,
    };
  }

  private initializeExpenseMonthOptions(): void {
    const monthFormatter = new Intl.DateTimeFormat('en-IN', {
      month: 'long',
      year: 'numeric',
    });
    const options: ExpenseMonthOption[] = [];
    const baseDate = new Date();

    for (let offset = 0; offset < 6; offset++) {
      const optionDate = new Date(baseDate.getFullYear(), baseDate.getMonth() - offset, 1);
      const year = optionDate.getFullYear();
      const month = optionDate.getMonth() + 1;
      options.push({
        value: `${year}-${`${month}`.padStart(2, '0')}`,
        label: monthFormatter.format(optionDate),
        year,
        month,
      });
    }

    this.expenseMonthOptions = options;
    this.selectedExpenseMonthValue = options[0]?.value ?? '';
  }

  private loadMonthlyExpenses(): void {
    const selectedMonth = this.expenseMonthOptions.find((option) => option.value === this.selectedExpenseMonthValue);
    if (!selectedMonth) {
      this.monthlyExpenses = [];
      this.hasLoadedMonthlyExpenses = false;
      this.isLoadingMonthlyExpenses = false;
      return;
    }

    this.isLoadingMonthlyExpenses = true;
    this.monthlyExpenses = [];
    const requestVersion = ++this.expensesRequestVersion;
    const branchVersion = this.branchStateVersion;

    this.http.get<BranchExpense[]>(
      '/api/manager/expenses',
      { headers: this.buildActorHeaders(), params: { year: selectedMonth.year, month: selectedMonth.month } as any },
    ).subscribe({
      next: (expenses) => {
        if (requestVersion !== this.expensesRequestVersion || branchVersion !== this.branchStateVersion) {
          return;
        }
        this.monthlyExpenses = expenses ?? [];
        this.hasLoadedMonthlyExpenses = true;
        this.isLoadingMonthlyExpenses = false;
      },
      error: (err) => {
        if (requestVersion !== this.expensesRequestVersion || branchVersion !== this.branchStateVersion) {
          return;
        }
        console.error('Failed to load monthly expenses', err);
        this.monthlyExpenses = [];
        this.hasLoadedMonthlyExpenses = false;
        this.isLoadingMonthlyExpenses = false;
        alert(err?.error?.message || 'Unable to load monthly expenses right now');
      },
    });
  }

  private buildActorHeaders(): HttpHeaders {
    const actorEmail = this.authService.getSnapshot()?.user.email ?? this.getStoredUserEmail();
    return actorEmail
      ? new HttpHeaders({ 'X-User-Email': actorEmail.trim() })
      : new HttpHeaders();
  }

  private resetFrameListsForBranchChange(): void {
    this.ongoingFrames = [];
    this.completedFrames = [];
    this.isLoadingOngoing = false;
    this.isLoadingCompleted = false;
  }

  private resetEarningsForBranchChange(): void {
    this.isLoadingEarnings = false;
    this.hasLoadedEarnings = false;
    this.todayEarnings = {
      totalEarnings: 0,
      totalDue: 0,
      duePlayers: [],
      settledPayments: [],
    };
  }

  private loadExpensePayers(): void {
    this.isLoadingExpensePayers = true;
    this.expensePayers = [];
    const requestVersion = ++this.expensePayersRequestVersion;
    const branchVersion = this.branchStateVersion;

    this.http.get<ExpensePayerOption[]>(
      '/api/manager/expenses/eligible-payers',
      { headers: this.buildActorHeaders() },
    ).subscribe({
      next: (payers) => {
        if (requestVersion !== this.expensePayersRequestVersion || branchVersion !== this.branchStateVersion) {
          return;
        }
        this.expensePayers = payers ?? [];
        this.isLoadingExpensePayers = false;
      },
      error: (err) => {
        if (requestVersion !== this.expensePayersRequestVersion || branchVersion !== this.branchStateVersion) {
          return;
        }
        console.error('Failed to load expense payers', err);
        this.expensePayers = [];
        this.isLoadingExpensePayers = false;
        alert(err?.error?.message || 'Unable to load eligible payers right now');
      },
    });
  }

  private resetMonthlyExpensesForBranchChange(): void {
    this.expensesRequestVersion++;
    this.expensePayersRequestVersion++;
    this.isLoadingMonthlyExpenses = false;
    this.hasLoadedMonthlyExpenses = false;
    this.monthlyExpenses = [];
    this.isAddExpenseExpanded = false;
    this.isLoadingExpensePayers = false;
    this.expensePayers = [];
    this.isSavingExpense = false;
    this.initializeExpenseMonthOptions();
    this.resetExpenseForm();
  }

  private resetExpenseForm(): void {
    this.expenseForm = {
      expenseName: '',
      amount: null,
      expenseType: '',
      paidByUserId: null,
      expenseDate: this.maxCompletedDate || this.formatDate(new Date()),
      notes: '',
    };
  }

  private getExpenseMonthLabel(value: string): string {
    return this.expenseMonthOptions.find((option) => option.value === value)?.label ?? '';
  }

  getSelectedExpenseMonthLabel(): string {
    return this.getExpenseMonthLabel(this.selectedExpenseMonthValue);
  }

  getMonthlyExpenseTotal(): number {
    return this.monthlyExpenses.reduce((total, expense) => total + this.toNumber(expense.amount), 0);
  }

  formatExpenseTypeLabel(expenseType: string | null | undefined): string {
    if (!expenseType) {
      return '-';
    }

    return expenseType
      .toLowerCase()
      .split('_')
      .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
      .join(' ');
  }

  private loadParentChildren(parentId: number): void {
    this.isLoadingParentChildren = true;
    this.http.get<ChildProfile[]>(`/api/children/by-parent?parentUserId=${parentId}`).subscribe({
      next: (children) => {
        this.parentChildren = children ?? [];
        this.isLoadingParentChildren = false;
      },
      error: (err) => {
        console.error('Failed to load parent children', err);
        this.parentChildren = [];
        this.isLoadingParentChildren = false;
      },
    });
  }

  private resetChildForm(): void {
    this.childForm = {
      name: '',
      dateOfBirth: '',
    };
  }

  private mapSearchResults(
    users: CustomerSearchResult[] | null | undefined,
    query: string,
    includePhoneAndEmail = false,
  ): CustomerSearchResult[] {
    const normalizedQuery = query.trim().toLowerCase();
    return (users ?? []).filter((user) => {
      const name = (user?.name ?? '').trim().toLowerCase();
      const email = (user?.email ?? '').trim().toLowerCase();
      const phone = (user?.phone ?? '').trim().toLowerCase();
      if (!normalizedQuery) {
        return !!name;
      }
      if (name.includes(normalizedQuery)) {
        return true;
      }
      if (!includePhoneAndEmail) {
        return false;
      }
      return email.includes(normalizedQuery) || phone.includes(normalizedQuery);
    });
  }

  private clearSelectedUpdateCustomer(): void {
    this.selectedUpdateCustomer = null;
    this.updateCustomerForm = {
      userId: null,
      name: '',
      email: '',
      phone: '',
    };
  }

  private loadOnboardingContext(requestedOrganizationId?: number): void {
    const actorEmail = this.getStoredUserEmail();
    if (!actorEmail) {
      return;
    }

    this.isLoadingOnboardingContext = true;
    const querySuffix = requestedOrganizationId ? `&organizationId=${requestedOrganizationId}` : '';
    this.http.get<CustomerOnboardingContext>(
      `/api/manager/customer-onboarding/context?email=${encodeURIComponent(actorEmail)}${querySuffix}`,
    ).subscribe({
      next: (context) => {
        this.onboardingContext = context;
        this.selectedOnboardingOrganizationId =
          requestedOrganizationId
          ?? context.currentOrganizationId
          ?? context.organizations[0]?.id
          ?? null;
        this.isLoadingOnboardingContext = false;
        this.syncOnboardingSelectionWithMembership();
      },
      error: (err) => {
        console.error('Failed to load onboarding context', err);
        this.onboardingContext = null;
        this.isLoadingOnboardingContext = false;
        alert(err?.error?.message || 'Unable to load onboarding options right now');
      },
    });
  }

  private syncOnboardingSelectionWithMembership(): void {
    const existingMembership = this.getSelectedOrganizationMembership();
    if (existingMembership) {
      this.selectedOnboardingBranchIds = (existingMembership.accessibleBranches ?? []).map((branch) => branch.id);
      this.selectedOnboardingBaseBranchId = existingMembership.baseBranchId ?? null;
      return;
    }

    this.selectedOnboardingBranchIds = this.selectedOnboardingBranchIds.filter((branchId) =>
      this.getAvailableOnboardingBranches().some((branch) => branch.id === branchId),
    );
    this.syncOnboardingBaseBranchSelection();
  }

  private syncOnboardingBaseBranchSelection(): void {
    const existingMembership = this.getSelectedOrganizationMembership();
    if (existingMembership) {
      this.selectedOnboardingBaseBranchId = existingMembership.baseBranchId ?? null;
      return;
    }

    if (this.selectedOnboardingBranchIds.length === 1) {
      this.selectedOnboardingBaseBranchId = this.selectedOnboardingBranchIds[0];
      return;
    }

    if (
      this.selectedOnboardingBaseBranchId !== null
      && !this.selectedOnboardingBranchIds.includes(this.selectedOnboardingBaseBranchId)
    ) {
      this.selectedOnboardingBaseBranchId = null;
    }
  }

  private getStoredUserEmail(): string | null {
    const storedUser = localStorage.getItem('user');
    if (!storedUser) {
      return null;
    }

    try {
      const authUser = JSON.parse(storedUser) as { email?: string };
      return authUser.email?.trim().toLowerCase() || null;
    } catch (error) {
      console.error('Failed to parse stored user', error);
      return null;
    }
  }

  private buildOnboardingSearchLabel(user: CustomerSearchResult): string {
    const phone = user.phone?.trim();
    return phone ? `${user.name} (${phone})` : user.name;
  }

  getAvailableOnboardingBranches(): OnboardingBranchOption[] {
    return this.onboardingContext?.branches ?? [];
  }

  getSelectedOnboardingBranches(): OnboardingBranchOption[] {
    const selectedBranchIds = new Set(this.selectedOnboardingBranchIds);
    const mergedOptions = new Map<number, OnboardingBranchOption>();

    for (const branch of this.getSelectedOrganizationMembership()?.accessibleBranches ?? []) {
      mergedOptions.set(branch.id, branch);
    }

    for (const branch of this.getAvailableOnboardingBranches()) {
      mergedOptions.set(branch.id, branch);
    }

    return Array.from(mergedOptions.values()).filter((branch) => selectedBranchIds.has(branch.id));
  }

  isOnboardingBranchSelected(branchId: number): boolean {
    return this.selectedOnboardingBranchIds.includes(branchId);
  }

  getSelectedOrganizationMembership(): CustomerMembershipSummary | null {
    if (!this.selectedOnboardingCandidate || !this.selectedOnboardingOrganizationId) {
      return null;
    }

    return this.selectedOnboardingCandidate.memberships.find(
      (membership) => membership.organizationId === this.selectedOnboardingOrganizationId,
    ) ?? null;
  }

  hasExistingMembershipForSelectedOrganization(): boolean {
    return !!this.getSelectedOrganizationMembership();
  }

  isExistingMembershipBranch(branchId: number): boolean {
    return (this.getSelectedOrganizationMembership()?.accessibleBranches ?? []).some((branch) => branch.id === branchId);
  }

  shouldShowOnboardingBaseBranchSelector(): boolean {
    return !this.hasExistingMembershipForSelectedOrganization() && this.selectedOnboardingBranchIds.length > 0;
  }

  getSelectedOnboardingOrganizationName(): string {
    const organizationId = this.selectedOnboardingOrganizationId;
    if (!organizationId) {
      return '';
    }

    return this.onboardingContext?.organizations.find((organization) => organization.id === organizationId)?.name
      ?? this.onboardingContext?.currentOrganizationName
      ?? '';
  }

  getSelectedOnboardingBaseBranchName(): string {
    if (!this.selectedOnboardingBaseBranchId) {
      return this.getSelectedOrganizationMembership()?.baseBranchName ?? '';
    }
    return this.getAvailableOnboardingBranches().find((branch) => branch.id === this.selectedOnboardingBaseBranchId)?.name
      ?? this.getSelectedOrganizationMembership()?.baseBranchName
      ?? '';
  }

  openSettlementPopup(player: DuePlayer): void {
    if (!player.userId) {
      alert('User ID is missing');
      return;
    }
    this.settlementPlayer = player;
    this.showSettlementPopup = true;
    this.isLoadingSettlementDetails = true;
    this.settlementTotalDue = 0;
    this.settlementFrameDue = 0;
    this.settlementConsumableDue = 0;
    this.settlementKidsDue = 0;
    this.settleAmount = null;
    this.discountAmount = null;
    this.paymentMode = '';

    const headers = this.buildActorHeaders();
    this.http.get<any>(
      `/api/user/payment-summary-by-date/current-branch?userId=${player.userId}&date=${this.selectedEarningsDate}`,
      { headers },
    ).subscribe({
      next: (summary) => {
        this.settlementFrameDue = summary?.frameDue ?? 0;
        this.settlementConsumableDue = summary?.consumableDue ?? 0;
        this.settlementKidsDue = summary?.kidsDue ?? 0;
        this.settlementTotalDue = this.settlementFrameDue + this.settlementConsumableDue + this.settlementKidsDue;
        this.isLoadingSettlementDetails = false;
      },
      error: (err) => {
        console.error('Failed to load payment summary', err);
        alert('Failed to load detailed dues. Settlement might be inaccurate.');
        this.isLoadingSettlementDetails = false;
      }
    });
  }

  openItemsPopup(player: DuePlayer): void {
    if (!player.userId) {
      alert('User ID is missing');
      return;
    }

    this.itemsPlayer = player;
    this.showItemsPopup = true;
    this.isLoadingItemsBreakdown = true;
    this.dueBreakdown = {
      frames: [],
      consumables: [],
      kidsPlay: [],
      frameDue: 0,
      consumableDue: 0,
      kidsDue: 0,
      totalDue: 0,
    };

    const headers = this.buildActorHeaders();
    this.http.get<PendingDueBreakdown>(
      `/api/user/payment-breakdown-by-date/current-branch?userId=${player.userId}&date=${this.selectedEarningsDate}`,
      { headers },
    ).subscribe({
      next: (breakdown) => {
        this.dueBreakdown = {
          frames: breakdown?.frames ?? [],
          consumables: breakdown?.consumables ?? [],
          kidsPlay: breakdown?.kidsPlay ?? [],
          frameDue: breakdown?.frameDue ?? 0,
          consumableDue: breakdown?.consumableDue ?? 0,
          kidsDue: breakdown?.kidsDue ?? 0,
          totalDue: breakdown?.totalDue ?? 0,
        };
        this.isLoadingItemsBreakdown = false;
      },
      error: (err) => {
        console.error('Failed to load due breakdown', err);
        this.isLoadingItemsBreakdown = false;
        alert('Failed to load pending item details');
      }
    });
  }

  closeSettlementPopup(): void {
    this.showSettlementPopup = false;
    this.settlementPlayer = null;
  }

  closeItemsPopup(): void {
    this.showItemsPopup = false;
    this.itemsPlayer = null;
  }

  getEffectiveSettlement(): number {
    return (this.settleAmount || 0) + (this.discountAmount || 0);
  }

  getRemainingDue(): number {
    return Math.max(0, this.settlementTotalDue - this.getEffectiveSettlement());
  }

  getMaxDiscount(): number {
    return Math.floor(this.settlementTotalDue * 0.6);
  }

  canSaveSettlement(): boolean {
    return !this.isSavingSettlement &&
           !this.isLoadingSettlementDetails &&
           this.settleAmount !== null && 
           this.settleAmount > 0 &&
           (this.discountAmount || 0) >= 0 &&
           (this.discountAmount || 0) <= this.getMaxDiscount() &&
           this.getEffectiveSettlement() <= this.settlementTotalDue &&
           this.paymentMode !== '';
  }

  saveSettlement(): void {
    if (!this.canSaveSettlement() || !this.settlementPlayer?.userId) return;

    this.isSavingSettlement = true;
    const request = {
      userId: this.settlementPlayer.userId,
      date: this.selectedEarningsDate,
      paidAmount: this.settleAmount || 0,
      discount: this.discountAmount || 0,
      paymentMode: this.paymentMode
    };

    this.http.post('/api/payment/settle-by-date', request, {
      headers: this.buildActorHeaders(),
      responseType: 'text',
    }).subscribe({
      next: () => {
        this.isSavingSettlement = false;
        alert('Payment settled successfully for selected date');
        this.closeSettlementPopup();
        this.loadEarningsForSelectedDate();
      },
      error: (err) => {
        console.error('Settlement failed', err);
        this.isSavingSettlement = false;
        alert(err?.error?.message || 'Payment settlement failed');
      }
    });
  }
}
