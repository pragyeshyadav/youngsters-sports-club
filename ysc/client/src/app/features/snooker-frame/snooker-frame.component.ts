import { HttpClient } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { BrandTitleComponent } from '../../shared/components/brand-title/brand-title.component';
import { ClubLogoComponent } from '../../shared/components/club-logo/club-logo.component';

interface Table {
  id: number;
  tableName: string;
}

interface BackendUser {
  id: number;
  email: string;
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

@Component({
  selector: 'app-snooker-frame',
  standalone: true,
  imports: [FormsModule, BrandTitleComponent, ClubLogoComponent],
  templateUrl: './snooker-frame.component.html',
  styleUrl: './snooker-frame.component.scss',
})
export class SnookerFrameComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  tables: Table[] = [];
  activeFrame: ActiveFrame | null = null;
  players: ActiveFramePlayer[] = [];
  framePlayers: ActiveFramePlayer[] = [];
  timerSeconds = 0;
  billAmount: number | null = null;
  billDuration: number | null = null;
  showEndPopup = false;
  winnerId: number | null = null;
  looserId: number | null = null;
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
      },
      error: (err) => {
        console.error('Failed to load frame players', err);
        this.framePlayers = [];
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
          this.loadTables();
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

  private loadActiveFrameOrTables(): void {
    const email = this.auth.getSnapshot()?.user.email;
    if (!email) {
      this.loadTables();
      return;
    }

    this.http.get<BackendUser>(`/api/user?email=${encodeURIComponent(email)}`).subscribe({
      next: (user) => {
        this.http.get<ActiveFrameResponse | null>(`/api/frame/active?userId=${user.id}`).subscribe({
          next: (res) => {
            if (res?.frame) {
              this.activeFrame = res.frame;
              this.players = res.players ?? [];
              this.startTimerFromServerTime();
              return;
            }

            this.loadTables();
          },
          error: (err) => {
            console.error('Failed to load active frame', err);
            this.loadTables();
          },
        });
      },
      error: (err) => {
        console.error('Failed to load current user', err);
        this.loadTables();
      },
    });
  }

  private loadTables(): void {
    this.http.get<Table[]>('/api/snooker/tables').subscribe({
      next: (res) => {
        this.tables = res;
      },
      error: (err) => {
        console.error('Failed to fetch tables', err);
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
    }, 1000);
  }

  private clearTimer(): void {
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
      this.timerInterval = null;
    }
  }
}
