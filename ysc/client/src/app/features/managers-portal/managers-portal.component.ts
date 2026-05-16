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

interface DuePlayer {
  userId?: number;
  name: string;
  due: number | string | null;
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
}

interface MessageResponse {
  message: string;
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
  private readonly today = new Date();

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
      this.loadEarningsForSelectedDate();
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
          settledPayments: earnings?.settledPayments ?? [],
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

    this.http.get<TodayEarnings>(`/api/manager/earnings?date=${this.selectedEarningsDate}`).subscribe({
      next: (earnings) => {
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
      this.loadCompletedFrames(true);
    }
  }

  loadCompletedFrames(useTodayApi: boolean = false): void {
    this.isLoadingCompleted = true;

    const request$ = useTodayApi
      ? this.http.get<CompletedFrame[]>('/api/frame/completed/today')
      : this.http.get<CompletedFrame[]>(`/api/frame/completed?date=${this.selectedCompletedDate}`);

    request$.subscribe({
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
  }

  toggleAddChild(): void {
    this.isAddChildExpanded = !this.isAddChildExpanded;
  }

  toggleUpdateCustomer(): void {
    this.isUpdateCustomerExpanded = !this.isUpdateCustomerExpanded;
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
        this.childParentResults = (users ?? []).filter((user) => (user.role ?? '') === 'CUSTOMER');
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
        this.updateCustomerResults = (users ?? []).filter((user) => (user.role ?? '') === 'CUSTOMER');
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
    this.http.post<MessageResponse>('/api/users/create-customer', {
      name: this.customerForm.name.trim(),
      email: this.customerForm.email.trim().toLowerCase(),
      mobileNumber: this.customerForm.mobileNumber.trim(),
    }).subscribe({
      next: (response) => {
        this.isSavingCustomer = false;
        alert(response?.message || 'Customer added successfully');
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

  private clearSelectedUpdateCustomer(): void {
    this.selectedUpdateCustomer = null;
    this.updateCustomerForm = {
      userId: null,
      name: '',
      email: '',
      phone: '',
    };
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

    this.http.get<any>(`/api/user/payment-summary-by-date?userId=${player.userId}&date=${this.selectedEarningsDate}`).subscribe({
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

  closeSettlementPopup(): void {
    this.showSettlementPopup = false;
    this.settlementPlayer = null;
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

    this.http.post('/api/payment/settle-by-date', request, { responseType: 'text' }).subscribe({
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
