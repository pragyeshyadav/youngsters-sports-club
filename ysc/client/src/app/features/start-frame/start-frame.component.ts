import { AsyncPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthUser } from '../../core/models/auth.models';
import { AuthService } from '../../core/services/auth.service';
import { BrandTitleComponent } from '../../shared/components/brand-title/brand-title.component';
import { ClubLogoComponent } from '../../shared/components/club-logo/club-logo.component';

interface SnookerTable {
  id: number;
  tableName: string;
  ratePerMinute?: number;
}

interface Player {
  id: number;
  name: string;
  email: string;
  phone?: string;
}

interface BackendUser {
  id: number;
  name: string;
  email: string;
  phone?: string;
}

interface StartFramePayload {
  tableId: number;
  startedBy: number;
  players: Array<{
    userId: number;
    name: string;
  }>;
}

interface FramePlayerOption {
  id?: number;
  userId?: number | null;
  name?: string;
  playerName?: string;
}

interface EndFrameResponse {
  duration: number;
  amount: number;
  frameId: number;
  tableId: number;
}

@Component({
  selector: 'app-start-frame',
  standalone: true,
  imports: [AsyncPipe, FormsModule, BrandTitleComponent, ClubLogoComponent],
  templateUrl: './start-frame.component.html',
  styleUrl: './start-frame.component.scss',
})
export class StartFrameComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  readonly user$: Observable<AuthUser | null> = this.auth.user$;

  selectedTable: SnookerTable | null = null;
  searchText = '';
  players: Player[] = [];
  selectedPlayers: Player[] = [];
  authUser: BackendUser | null = null;
  frameStarted = false;
  frameId: number | null = null;
  seconds = 0;
  showEndPopup = false;
  winnerId: number | null = null;
  looserId: number | null = null;
  framePlayers: FramePlayerOption[] = [];
  billAmount: number | null = null;
  billDuration: number | null = null;
  private timerInterval: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    this.selectedTable = (history.state?.table as SnookerTable | undefined) ?? null;

    if (!this.selectedTable) {
      void this.router.navigate(['/snooker-frame']);
      return;
    }

    this.loadCurrentUser();
  }

  ngOnDestroy(): void {
    this.clearTimer();
  }

  searchPlayers(): void {
    if (this.frameStarted) {
      return;
    }

    const query = this.searchText.trim();

    if (!query) {
      this.players = [];
      return;
    }

    this.http
      .get<Player[]>(`/api/users/search?query=${encodeURIComponent(query)}`)
      .subscribe({
        next: (res) => {
          const selectedIds = new Set(this.selectedPlayers.map((player) => player.id));
          this.players = res.filter((player) => !selectedIds.has(player.id));
        },
        error: (err) => {
          console.error('Failed to search players', err);
          this.players = [];
        },
      });
  }

  addPlayer(player: Player): void {
    if (this.frameStarted) {
      return;
    }

    if (this.selectedPlayers.length >= 6) {
      alert('Maximum 6 players allowed');
      return;
    }

    const exists = this.selectedPlayers.some((selected) => selected.id === player.id);
    if (exists) {
      return;
    }

    this.selectedPlayers = [...this.selectedPlayers, player];
    this.searchText = '';
    this.players = [];
  }

  removePlayer(player: Player): void {
    if (this.frameStarted) {
      return;
    }

    this.selectedPlayers = this.selectedPlayers.filter((selected) => selected.id !== player.id);
  }

  startFrame(): void {
    if (this.selectedPlayers.length === 0) {
      alert('Select at least one player');
      return;
    }

    if (!this.selectedTable?.id || !this.authUser?.id) {
      alert('Unable to start frame. Please refresh and try again.');
      return;
    }

    const request: StartFramePayload = {
      tableId: this.selectedTable.id,
      startedBy: this.authUser.id,
      players: this.selectedPlayers.map((player) => ({
        userId: player.id,
        name: player.name,
      })),
    };

    this.http.post<number>('/api/frame/start', request).subscribe({
      next: (frameId) => {
        this.frameStarted = true;
        this.frameId = frameId;
        this.billAmount = null;
        this.billDuration = null;
        this.startTimer();
      },
      error: (err) => {
        console.error('Failed to start frame', err);
        alert('Unable to start frame right now');
      },
    });
  }

  openEndPopup(): void {
    if (!this.frameId) {
      return;
    }

    this.showEndPopup = true;
    this.winnerId = null;
    this.looserId = null;

    if (this.selectedPlayers.length > 0) {
      this.framePlayers = this.selectedPlayers;
      return;
    }

    this.http.get<FramePlayerOption[]>(`/api/frame/${this.frameId}/players`).subscribe({
      next: (res) => {
        this.framePlayers = res;
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
    if (!this.frameId || !this.canEndFrame()) {
      return;
    }

    this.http
      .post<EndFrameResponse>(`/api/frame/end/${this.frameId}`, {
        winnerId: this.winnerId,
        looserId: this.looserId,
      })
      .subscribe({
        next: (res) => {
          this.clearTimer();
          this.showEndPopup = false;
          this.billAmount = res.amount;
          this.billDuration = res.duration;
          this.frameStarted = false;
          this.frameId = null;
          this.searchText = '';
          this.players = [];
          this.selectedPlayers = [];
          this.framePlayers = [];
          this.winnerId = null;
          this.looserId = null;
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

  goBack(): void {
    if (this.frameStarted) {
      return;
    }
    void this.router.navigate(['/snooker-frame']);
  }

  get formattedTime(): string {
    const mins = Math.floor(this.seconds / 60);
    const secs = this.seconds % 60;
    return `${mins}:${secs < 10 ? '0' + secs : secs}`;
  }

  private startTimer(): void {
    this.clearTimer();
    this.seconds = 0;
    this.timerInterval = setInterval(() => {
      this.seconds += 1;
    }, 1000);
  }

  private clearTimer(): void {
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
      this.timerInterval = null;
    }
  }

  private loadCurrentUser(): void {
    const email = this.auth.getSnapshot()?.user.email;
    if (!email) {
      return;
    }

    this.http.get<BackendUser>(`/api/user?email=${encodeURIComponent(email)}`).subscribe({
      next: (user) => {
        this.authUser = user;
      },
      error: (err) => {
        console.error('Failed to load current user', err);
      },
    });
  }
}
