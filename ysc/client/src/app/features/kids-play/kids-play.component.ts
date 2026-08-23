import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { OrganizationContextService } from '../../core/services/organization-context.service';
import { Subscription } from 'rxjs';

interface SettlementUser {
  id: number;
  name: string;
  email: string;
  role?: string;
}

interface ChildProfile {
  id: number;
  name: string;
  dateOfBirth: string;
  address?: string | null;
  school?: string | null;
}

interface KidsSession {
  sessionId: number;
  childId: number;
  childName: string;
  parentUserId?: number;
  parentName?: string;
  startTime: string;
  endTime?: string | null;
  durationMinutes?: number | null;
  ratePerMinute?: number | string | null;
  totalAmount?: number | string | null;
  paymentStatus?: string;
  status?: string;
}

@Component({
  selector: 'app-kids-play',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './kids-play.component.html',
  styleUrl: './kids-play.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class KidsPlayComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly router = inject(Router);
  private readonly organizationContextService = inject(OrganizationContextService);
  private readonly subscriptions = new Subscription();
  private currentBranchId: number | null = null;
  private branchRequestVersion = 0;

  currentUserId: number | null = null;
  isManagerOrAdmin = false;

  parentSearchText = '';
  searchedParents: SettlementUser[] = [];
  selectedParent: SettlementUser | null = null;
  isLoadingParents = false;

  children: ChildProfile[] = [];
  isLoadingChildren = false;

  allActiveSessions: KidsSession[] = [];
  parentActiveSessions: KidsSession[] = [];
  isGlobalPanelExpanded = false;

  isSubmittingChild = false;
  showAddChildForm = false;
  
  childForm = {
    name: '',
    dateOfBirth: '',
    address: '',
    school: '',
  };

  currentTime = Date.now();
  private timerInterval: any = null;

  ngOnInit(): void {
    const email = this.auth.getSnapshot()?.user.email;
    if (!email) return;

    this.http.get<any>(`/api/user?email=${encodeURIComponent(email)}`).subscribe({
      next: (user) => {
        this.currentUserId = user.id;
        this.isManagerOrAdmin = ['MANAGER', 'ADMIN', 'SUPER_ADMIN'].includes(user.role);
        
        if (this.isManagerOrAdmin) {
          this.loadAllActiveSessions();
        } else {
          this.loadChildren();
          this.loadParentActiveSessions(this.currentUserId!);
        }
        this.subscribeToBranchChanges();
        this.startGlobalTimer();
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load current user', err);
      },
    });
  }

  ngOnDestroy(): void {
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
    }
    this.subscriptions.unsubscribe();
  }

  startGlobalTimer() {
    this.timerInterval = setInterval(() => {
      this.currentTime = Date.now();
      this.cdr.markForCheck();
    }, 1000);
  }

  getElapsedTimer(startTime: string): string {
    if (!startTime) return '0:00';
    const start = new Date(startTime).getTime();
    const secondsElapsed = Math.max(0, Math.floor((this.currentTime - start) / 1000));
    const mins = Math.floor(secondsElapsed / 60);
    const secs = secondsElapsed % 60;
    return `${mins}:${secs < 10 ? '0' + secs : secs}`;
  }

  get canAddMoreChildren(): boolean {
    return this.children.length < 10;
  }

  toggleAddChildForm(): void {
    if (!this.canAddMoreChildren) {
      alert('Maximum 10 children allowed per parent');
      return;
    }

    this.showAddChildForm = !this.showAddChildForm;
    if (!this.showAddChildForm) {
      this.resetChildForm();
    }
  }

  saveChild(): void {
    const parentId = this.isManagerOrAdmin ? this.selectedParent?.id : this.currentUserId;
    if (!parentId) return;

    if (!this.childForm.name.trim() || !this.childForm.dateOfBirth) {
      alert('Name and date of birth are required');
      return;
    }

    if (!this.canAddMoreChildren) {
      alert('Maximum 10 children allowed per parent');
      return;
    }

    this.isSubmittingChild = true;
    this.http.post<ChildProfile>('/api/children', {
      parentUserId: parentId,
      name: this.childForm.name.trim(),
      dateOfBirth: this.childForm.dateOfBirth,
      address: this.childForm.address.trim(),
      school: this.childForm.school.trim(),
    }).subscribe({
      next: (child) => {
        this.children = [child, ...this.children];
        this.isSubmittingChild = false;
        this.showAddChildForm = false;
        this.resetChildForm();
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to add child', err);
        this.isSubmittingChild = false;
        alert('Unable to add child right now');
        this.cdr.markForCheck();
      },
    });
  }

  searchParents(): void {
    const query = this.parentSearchText.trim();
    if (query.length < 3) {
      this.searchedParents = [];
      return;
    }
    
    this.isLoadingParents = true;
    this.http.get<SettlementUser[]>(`/api/users/search?query=${encodeURIComponent(query)}`).subscribe({
      next: (users) => {
        this.searchedParents = users.filter((u: any) => !u.role || u.role === 'CUSTOMER');
        this.isLoadingParents = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.searchedParents = [];
        this.isLoadingParents = false;
        this.cdr.markForCheck();
      }
    });
  }

  selectParent(user: SettlementUser): void {
    this.selectedParent = user;
    this.parentSearchText = user.name;
    this.searchedParents = [];
    this.loadChildren();
    this.loadParentActiveSessions(user.id);
  }

  clearSelectedParent(): void {
    this.selectedParent = null;
    this.parentSearchText = '';
    this.children = [];
    this.parentActiveSessions = [];
    this.searchedParents = [];
  }

  toggleGlobalPanel(): void {
    this.isGlobalPanelExpanded = !this.isGlobalPanelExpanded;
    if (this.isGlobalPanelExpanded) {
      this.loadAllActiveSessions();
    }
  }

  private loadChildren(): void {
    const parentId = this.isManagerOrAdmin ? this.selectedParent?.id : this.currentUserId;
    if (!parentId) return;

    this.isLoadingChildren = true;
    const requestVersion = this.branchRequestVersion;
    this.http.get<ChildProfile[]>(`/api/children/by-parent?parentUserId=${parentId}`).subscribe({
      next: (children) => {
        if (requestVersion !== this.branchRequestVersion) {
          return;
        }
        this.children = children ?? [];
        this.isLoadingChildren = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        if (requestVersion !== this.branchRequestVersion) {
          return;
        }
        console.error('Failed to load children', err);
        this.children = [];
        this.isLoadingChildren = false;
        this.cdr.markForCheck();
      },
    });
  }

  private loadParentActiveSessions(parentId: number): void {
    const requestVersion = this.branchRequestVersion;
    this.http.get<KidsSession[]>(
      `/api/kids-session/active?parentUserId=${parentId}`,
      { headers: this.buildActorHeaders() },
    ).subscribe({
      next: (sessions) => {
        if (requestVersion !== this.branchRequestVersion) {
          return;
        }
        this.parentActiveSessions = sessions || [];
        this.cdr.markForCheck();
      },
      error: (err) => console.error('Failed to load parent sessions', err)
    });
  }

  private loadAllActiveSessions(): void {
    const requestVersion = this.branchRequestVersion;
    this.http.get<KidsSession[]>(
      `/api/kids-session/active`,
      { headers: this.buildActorHeaders() },
    ).subscribe({
      next: (sessions) => {
        if (requestVersion !== this.branchRequestVersion) {
          return;
        }
        this.allActiveSessions = sessions || [];
        this.cdr.markForCheck();
      },
      error: (err) => console.error('Failed to load global sessions', err)
    });
  }

  getChildSession(childId: number): KidsSession | undefined {
    return this.parentActiveSessions.find(s => s.childId === childId);
  }

  startPlayTime(childId: number): void {
    const parentId = this.isManagerOrAdmin ? this.selectedParent?.id : this.currentUserId;
    if (!parentId || !childId) return;

    this.http.post<KidsSession>(
      '/api/kids-session/start',
      {
        parentUserId: parentId,
        childId: childId,
      },
      { headers: this.buildActorHeaders() },
    ).subscribe({
      next: (session) => {
        this.parentActiveSessions = [session, ...this.parentActiveSessions];
        if (this.isManagerOrAdmin) {
          this.loadAllActiveSessions();
        }
        this.cdr.markForCheck();
      },
      error: (err) => {
        alert(err.error?.message || 'Unable to start play time right now');
      },
    });
  }

  endPlayTime(sessionId: number): void {
    const session = this.parentActiveSessions.find(s => s.sessionId === sessionId) || this.allActiveSessions.find(s => s.sessionId === sessionId);
    if (!session) return;

    this.http.post<KidsSession>(
      '/api/kids-session/end',
      {
        parentUserId: session.parentUserId,
        sessionId: sessionId,
      },
      { headers: this.buildActorHeaders() },
    ).subscribe({
      next: (resultSession) => {
        this.parentActiveSessions = this.parentActiveSessions.filter(s => s.sessionId !== sessionId);
        if (this.isManagerOrAdmin) {
          this.loadAllActiveSessions();
        }
        alert(`Play session ended. Total amount: ₹${resultSession.totalAmount ?? 0}`);
        this.cdr.markForCheck();
      },
      error: (err) => {
        alert(err.error?.message || 'Unable to end play time right now');
      },
    });
  }

  rejectPlayTime(sessionId: number): void {
    if (!confirm('Are you sure you want to reject and cancel this session? This will not incur any charges.')) return;
    
    this.http.post<KidsSession>(
      '/api/kids-session/reject',
      {
        sessionId: sessionId,
      },
      { headers: this.buildActorHeaders() },
    ).subscribe({
      next: () => {
        this.parentActiveSessions = this.parentActiveSessions.filter(s => s.sessionId !== sessionId);
        if (this.isManagerOrAdmin) {
          this.loadAllActiveSessions();
        }
        this.cdr.markForCheck();
      },
      error: (err) => {
        alert(err.error?.message || 'Unable to reject play time right now');
      },
    });
  }

  goBack(): void {
    void this.router.navigate(['/dashboard']);
  }

  private resetChildForm(): void {
    this.childForm = {
      name: '',
      dateOfBirth: '',
      address: '',
      school: '',
    };
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
        if (!branchId) {
          return;
        }

        if (this.isManagerOrAdmin) {
          if (this.isGlobalPanelExpanded) {
            this.loadAllActiveSessions();
          }
          return;
        }

        if (this.currentUserId) {
          this.loadChildren();
          this.loadParentActiveSessions(this.currentUserId);
        }
      }),
    );
  }

  private resetBranchScopedState(): void {
    this.branchRequestVersion++;
    this.parentSearchText = '';
    this.searchedParents = [];
    this.selectedParent = null;
    this.children = [];
    this.isLoadingChildren = false;
    this.allActiveSessions = [];
    this.parentActiveSessions = [];
    this.isLoadingParents = false;
    this.showAddChildForm = false;
    this.isSubmittingChild = false;
    this.resetChildForm();
    this.cdr.markForCheck();
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
