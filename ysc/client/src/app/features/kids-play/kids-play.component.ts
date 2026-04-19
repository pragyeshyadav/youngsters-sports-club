import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

interface BackendUser {
  id: number;
  email: string;
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
  startTime: string;
  endTime?: string | null;
  durationMinutes?: number | null;
  ratePerMinute?: number | string | null;
  totalAmount?: number | string | null;
  paymentStatus?: string;
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
  private timerInterval: ReturnType<typeof setInterval> | null = null;

  currentUserId: number | null = null;
  children: ChildProfile[] = [];
  activeSession: KidsSession | null = null;
  selectedChildId: number | null = null;
  isLoadingChildren = false;
  isLoadingActiveSession = false;
  isSubmittingChild = false;
  isStartingSession = false;
  isEndingSession = false;
  showAddChildForm = false;
  secondsElapsed = 0;

  childForm = {
    name: '',
    dateOfBirth: '',
    address: '',
    school: '',
  };

  ngOnInit(): void {
    const email = this.auth.getSnapshot()?.user.email;
    if (!email) {
      return;
    }

    this.http.get<BackendUser>(`/api/user?email=${encodeURIComponent(email)}`).subscribe({
      next: (user) => {
        this.currentUserId = user.id;
        this.loadChildren();
        this.loadActiveSession();
      },
      error: (err) => {
        console.error('Failed to load current user', err);
      },
    });
  }

  ngOnDestroy(): void {
    this.clearTimer();
  }

  get canAddMoreChildren(): boolean {
    return this.children.length < 10;
  }

  get formattedTimer(): string {
    const mins = Math.floor(this.secondsElapsed / 60);
    const secs = this.secondsElapsed % 60;
    return `${mins}:${secs < 10 ? '0' + secs : secs}`;
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
    if (!this.currentUserId) {
      return;
    }

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
      parentUserId: this.currentUserId,
      name: this.childForm.name.trim(),
      dateOfBirth: this.childForm.dateOfBirth,
      address: this.childForm.address.trim(),
      school: this.childForm.school.trim(),
    }).subscribe({
      next: (child) => {
        this.children = [child, ...this.children];
        this.selectedChildId ??= child.id;
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

  startPlayTime(): void {
    if (!this.currentUserId || !this.selectedChildId || this.isStartingSession) {
      return;
    }

    this.isStartingSession = true;
    this.http.post<KidsSession>('/api/kids-session/start', {
      parentUserId: this.currentUserId,
      childId: this.selectedChildId,
    }).subscribe({
      next: (session) => {
        this.activeSession = session;
        this.isStartingSession = false;
        this.startTimer(session.startTime);
        void this.router.navigate(['/kids-play']);
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to start kids play session', err);
        this.isStartingSession = false;
        alert('Unable to start play time right now');
        this.cdr.markForCheck();
      },
    });
  }

  endPlayTime(): void {
    if (!this.currentUserId || !this.activeSession?.sessionId || this.isEndingSession) {
      return;
    }

    this.isEndingSession = true;
    this.http.post<KidsSession>('/api/kids-session/end', {
      parentUserId: this.currentUserId,
      sessionId: this.activeSession.sessionId,
    }).subscribe({
      next: (session) => {
        this.activeSession = session;
        this.isEndingSession = false;
        this.clearTimer();
        alert(`Play session ended. Total amount: ₹${session.totalAmount ?? 0}`);
        this.loadActiveSession();
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to end kids play session', err);
        this.isEndingSession = false;
        alert('Unable to end play time right now');
        this.cdr.markForCheck();
      },
    });
  }

  goBack(): void {
    void this.router.navigate(['/dashboard']);
  }

  private loadChildren(): void {
    if (!this.currentUserId) {
      return;
    }

    this.isLoadingChildren = true;
    this.http.get<ChildProfile[]>(`/api/children/by-parent?parentUserId=${this.currentUserId}`).subscribe({
      next: (children) => {
        this.children = children ?? [];
        this.selectedChildId = this.children[0]?.id ?? null;
        this.isLoadingChildren = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load children', err);
        this.children = [];
        this.selectedChildId = null;
        this.isLoadingChildren = false;
        this.cdr.markForCheck();
      },
    });
  }

  private loadActiveSession(): void {
    if (!this.currentUserId) {
      return;
    }

    this.isLoadingActiveSession = true;
    this.http.get<KidsSession | null>(`/api/kids-session/active?parentUserId=${this.currentUserId}`).subscribe({
      next: (session) => {
        this.activeSession = session;
        this.isLoadingActiveSession = false;
        if (session?.startTime && !session?.endTime) {
          this.startTimer(session.startTime);
        } else {
          this.clearTimer();
        }
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load active session', err);
        this.activeSession = null;
        this.isLoadingActiveSession = false;
        this.clearTimer();
        this.cdr.markForCheck();
      },
    });
  }

  private startTimer(startTime: string): void {
    this.clearTimer();
    const start = new Date(startTime).getTime();
    this.secondsElapsed = Math.max(0, Math.floor((Date.now() - start) / 1000));
    this.timerInterval = setInterval(() => {
      this.secondsElapsed = Math.max(0, Math.floor((Date.now() - start) / 1000));
      this.cdr.markForCheck();
    }, 1000);
  }

  private clearTimer(): void {
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
      this.timerInterval = null;
    }
    this.secondsElapsed = 0;
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
