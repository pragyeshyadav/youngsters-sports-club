import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

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
    this.http.get<ChildProfile[]>(`/api/children/by-parent?parentUserId=${parentId}`).subscribe({
      next: (children) => {
        this.children = children ?? [];
        this.isLoadingChildren = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load children', err);
        this.children = [];
        this.isLoadingChildren = false;
        this.cdr.markForCheck();
      },
    });
  }

  private loadParentActiveSessions(parentId: number): void {
    this.http.get<KidsSession[]>(`/api/kids-session/active?parentUserId=${parentId}`).subscribe({
      next: (sessions) => {
        this.parentActiveSessions = sessions || [];
        this.cdr.markForCheck();
      },
      error: (err) => console.error('Failed to load parent sessions', err)
    });
  }

  private loadAllActiveSessions(): void {
    this.http.get<KidsSession[]>(`/api/kids-session/active`).subscribe({
      next: (sessions) => {
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

    this.http.post<KidsSession>('/api/kids-session/start', {
      parentUserId: parentId,
      childId: childId,
    }).subscribe({
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

    this.http.post<KidsSession>('/api/kids-session/end', {
      parentUserId: session.parentUserId,
      sessionId: sessionId,
    }).subscribe({
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
    
    this.http.post<KidsSession>('/api/kids-session/reject', {
      sessionId: sessionId,
    }).subscribe({
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
}
