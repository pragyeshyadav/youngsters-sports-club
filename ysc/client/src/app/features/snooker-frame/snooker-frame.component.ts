import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { BrandTitleComponent } from '../../shared/components/brand-title/brand-title.component';
import { ClubLogoComponent } from '../../shared/components/club-logo/club-logo.component';

interface Table {
  id: number;
  tableName: string;
  isAvailable?: boolean;
}

interface BackendUser {
  id: number;
  email: string;
  role: string;
}

interface ActiveFrame {
  id: number;
  tableId: number;
  tableName: string;
  startTime: string;
  status: string;
}

interface ActiveFramePlayer {
  id: number;
  userId?: number | null;
  playerName: string;
}

interface ActiveFrameResponse {
  frame: ActiveFrame;
  players: ActiveFramePlayer[];
}

interface EndFrameResponse {
  duration: number;
  amount: number;
  frameId: number;
  tableId: number;
}

interface OngoingFrameSummary {
  id: number;
  tableId: number | null;
  tableName: string | null;
  startTime: string;
  status: string;
  startedBy: string | null;
  players: string[];
}

@Component({
  selector: 'app-snooker-frame',
  standalone: true,
  imports: [CommonModule, FormsModule, BrandTitleComponent, ClubLogoComponent],
  templateUrl: './snooker-frame.component.html',
  styleUrl: './snooker-frame.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SnookerFrameComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);

  tables: Table[] = [];
  ongoingFrames: OngoingFrameSummary[] = [];
  activeFrame: ActiveFrame | null = null;
  players: ActiveFramePlayer[] = [];
  framePlayers: ActiveFramePlayer[] = [];
  timerSeconds = 0;
  billAmount: number | null = null;
  billDuration: number | null = null;
  showEndPopup = false;
  winnerId: number | null = null;
  looserId: number | null = null;
  userRole = '';
  isLoadingTables = false;
  isLoadingCurrentFrame = false;
  isLoadingOngoingFrames = false;
  private timerInterval: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    this.loadActiveFrameOrTables();
  }

  ngOnDestroy(): void {
    this.clearTimer();
  }

  selectTable(table: Table): void {
    void this.router.navigate(['/start-frame'], {
      state: { table },
    });
  }

  openEndPopup(): void {
    if (!this.activeFrame?.id) {
      return;
    }

    this.showEndPopup = true;
    this.winnerId = null;
    this.looserId = null;

    this.http.get<ActiveFramePlayer[]>(`/api/frame/${this.activeFrame.id}/players`).subscribe({
      next: (res) => {
        this.framePlayers = res.filter((player) => player.userId !== null && player.userId !== undefined);
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load frame players', err);
        this.framePlayers = [];
        this.cdr.markForCheck();
      },
    });
  }

  canEndFrame(): boolean {
    return this.winnerId !== null && this.looserId !== null && this.winnerId !== this.looserId;
  }

  confirmEndFrame(): void {
    if (!this.activeFrame?.id || !this.canEndFrame()) {
      return;
    }

    this.http
      .post<EndFrameResponse>(`/api/frame/end/${this.activeFrame.id}`, {
        winnerId: this.winnerId,
        looserId: this.looserId,
      })
      .subscribe({
        next: (res) => {
          this.clearTimer();
          this.showEndPopup = false;
          this.billAmount = res.amount;
          this.billDuration = res.duration;
          this.activeFrame = null;
          this.players = [];
          this.framePlayers = [];
          this.timerSeconds = 0;
          this.winnerId = null;
          this.looserId = null;
          if (this.isPrivileged()) {
            this.loadOngoingFrames();
          }
          this.loadTables();
          this.cdr.markForCheck();
        },
        error: (err) => {
          console.error('Failed to end frame', err);
          alert('Unable to end frame right now');
        },
      });
  }

  closeEndPopup(): void {
    this.showEndPopup = false;
  }

  get formattedTime(): string {
    const mins = Math.floor(this.timerSeconds / 60);
    const secs = this.timerSeconds % 60;
    return `${mins}:${secs < 10 ? '0' + secs : secs}`;
  }

  isPrivileged(): boolean {
    return ['MANAGER', 'ADMIN', 'SUPER_ADMIN'].includes(this.userRole);
  }

  showOngoingOnly(): boolean {
    return !!this.activeFrame && !this.isPrivileged();
  }

  goToTableSelection(): void {
    void this.router.navigate(['/snooker-frame']);
  }

  private loadActiveFrameOrTables(): void {
    const email = this.auth.getSnapshot()?.user.email;
    if (!email) {
      this.loadTables();
      return;
    }

    this.http.get<BackendUser>(`/api/user?email=${encodeURIComponent(email)}`).subscribe({
      next: (user) => {
        this.userRole = user.role ?? '';
        if (this.isPrivileged()) {
          this.activeFrame = null;
          this.players = [];
          this.loadOngoingFrames();
          this.loadTables();
          this.cdr.markForCheck();
          return;
        }

        this.isLoadingCurrentFrame = true;
        this.http.get<ActiveFrameResponse | null>(`/api/frame/active?userId=${user.id}`).subscribe({
          next: (res) => {
            if (res?.frame) {
              this.activeFrame = res.frame;
              this.players = res.players ?? [];
              this.startTimerFromServerTime();
              this.isLoadingCurrentFrame = false;
              this.cdr.markForCheck();
              return;
            }

            this.isLoadingCurrentFrame = false;
            this.loadTables();
            this.cdr.markForCheck();
          },
          error: (err) => {
            console.error('Failed to load active frame', err);
            this.isLoadingCurrentFrame = false;
            this.loadTables();
            this.cdr.markForCheck();
          },
        });
      },
      error: (err) => {
        console.error('Failed to load current user', err);
        this.loadTables();
        this.cdr.markForCheck();
      },
    });
  }

  private loadTables(): void {
    this.isLoadingTables = true;
    this.http.get<Table[]>('/api/snooker/tables').subscribe({
      next: (res) => {
        const availableTables = res.filter((table) => table.isAvailable !== false);
        this.tables = availableTables;
        this.isLoadingTables = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to fetch tables', err);
        this.tables = [];
        this.isLoadingTables = false;
        this.cdr.markForCheck();
      },
    });
  }

  private loadOngoingFrames(): void {
    this.isLoadingOngoingFrames = true;
    this.http.get<OngoingFrameSummary[]>('/api/frame/ongoing/today').subscribe({
      next: (res) => {
        this.ongoingFrames = res ?? [];
        this.isLoadingOngoingFrames = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load ongoing frames', err);
        this.ongoingFrames = [];
        this.isLoadingOngoingFrames = false;
        this.cdr.markForCheck();
      },
    });
  }

  private startTimerFromServerTime(): void {
    this.clearTimer();

    if (!this.activeFrame?.startTime) {
      this.timerSeconds = 0;
      return;
    }

    const startTime = new Date(this.activeFrame.startTime).getTime();
    this.timerSeconds = Math.floor((Date.now() - startTime) / 1000);

    this.timerInterval = setInterval(() => {
      const now = Date.now();
      this.timerSeconds = Math.floor((now - startTime) / 1000);
      this.cdr.markForCheck();
    }, 1000);
  }

  private clearTimer(): void {
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
      this.timerInterval = null;
    }
  }
}
