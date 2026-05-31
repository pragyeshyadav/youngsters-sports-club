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

interface RestartFramePayload {
  tableId: number;
  startedBy: number;
  players: Array<{
    userId: number;
    name: string;
  }>;
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

type FrameGameMode = 'SINGLE' | 'TEAM';

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
  gameMode: FrameGameMode = 'SINGLE';
  winnerId: number | null = null;
  looserId: number | null = null;
  winnerIds: number[] = [];
  loserIds: number[] = [];
  userRole = '';
  currentUserId: number | null = null;
  lastEndedTable: Table | null = null;
  lastEndedPlayers: ActiveFramePlayer[] = [];
  isLoadingTables = false;
  isLoadingCurrentFrame = false;
  isLoadingOngoingFrames = false;
  isOpeningEndPopup = false;
  isEndingFrame = false;
  isRestartingSameFrame = false;
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
    if (!this.activeFrame?.id || this.isOpeningEndPopup || this.isEndingFrame) {
      return;
    }

    this.showEndPopup = true;
    this.gameMode = 'SINGLE';
    this.winnerId = null;
    this.looserId = null;
    this.winnerIds = [];
    this.loserIds = [];
    this.isOpeningEndPopup = true;

    this.http.get<ActiveFramePlayer[]>(`/api/frame/${this.activeFrame.id}/players`).subscribe({
      next: (res) => {
        this.framePlayers = res.filter((player) => player.userId !== null && player.userId !== undefined);
        this.isOpeningEndPopup = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load frame players', err);
        this.framePlayers = [];
        this.isOpeningEndPopup = false;
        this.cdr.markForCheck();
      },
    });
  }

  get canUseTeamMode(): boolean {
    return this.framePlayers.length === 4;
  }

  get isTeamMatch(): boolean {
    return this.canUseTeamMode && this.gameMode === 'TEAM';
  }

  get playerCount(): number {
    return this.framePlayers.length;
  }

  get supportsDynamicLosers(): boolean {
    return this.playerCount === 3 || this.playerCount === 5 || this.playerCount === 6;
  }

  get maxLosersAllowed(): number {
    if (this.playerCount === 3) {
      return 2;
    }
    if (this.playerCount === 5 || this.playerCount === 6) {
      return 3;
    }
    return 1;
  }

  get autoWinnerIds(): number[] {
    if (this.playerCount !== 5 && this.playerCount !== 6) {
      return [];
    }
    return this.framePlayers
      .map((player) => this.resolveFramePlayerId(player))
      .filter((playerId): playerId is number => !!playerId && !this.loserIds.includes(playerId));
  }

  canEndFrame(): boolean {
    const eligiblePlayerIds = this.getEligibleFramePlayerIds();
    if (eligiblePlayerIds.length < 2) {
      return false;
    }

    if (this.isTeamMatch) {
      const winnerIds = this.getNormalizedSelection(this.winnerIds);
      const loserIds = this.getNormalizedSelection(this.loserIds);
      if (winnerIds.length !== 2 || loserIds.length !== 2) {
        return false;
      }
      const allSelected = new Set([...winnerIds, ...loserIds]);
      return allSelected.size === 4 && eligiblePlayerIds.every((playerId) => allSelected.has(playerId));
    }
    if (this.playerCount === 3) {
      if (this.winnerId === null) {
        return false;
      }
      const winnerId = Number(this.winnerId);
      const loserIds = this.getNormalizedSelection(this.loserIds);
      if (!eligiblePlayerIds.includes(winnerId) || loserIds.length < 1 || loserIds.length > 2) {
        return false;
      }
      if (loserIds.includes(winnerId)) {
        return false;
      }
      return loserIds.every((loserId) => eligiblePlayerIds.includes(loserId));
    }
    if (this.playerCount === 5 || this.playerCount === 6) {
      const loserIds = this.getNormalizedSelection(this.loserIds);
      return loserIds.length >= 1
        && loserIds.length <= 3
        && loserIds.every((loserId) => eligiblePlayerIds.includes(loserId))
        && loserIds.length < eligiblePlayerIds.length;
    }
    if (this.winnerId === null || this.looserId === null) {
      return false;
    }
    const winnerId = Number(this.winnerId);
    const loserId = Number(this.looserId);
    return eligiblePlayerIds.includes(winnerId)
      && eligiblePlayerIds.includes(loserId)
      && winnerId !== loserId;
  }

  onGameModeChange(mode: FrameGameMode): void {
    this.gameMode = mode;
    this.winnerId = null;
    this.looserId = null;
    this.winnerIds = [];
    this.loserIds = [];
  }

  toggleWinner(playerId: number | undefined | null): void {
    if (!playerId) {
      return;
    }
    if (this.winnerIds.includes(playerId)) {
      this.winnerIds = this.winnerIds.filter((id) => id !== playerId);
    } else if (this.winnerIds.length < 2) {
      this.winnerIds = [...this.winnerIds, playerId];
      this.loserIds = this.loserIds.filter((id) => id !== playerId);
    }
  }

  toggleLoser(playerId: number | undefined | null): void {
    if (!playerId) {
      return;
    }
    if (this.loserIds.includes(playerId)) {
      this.loserIds = this.loserIds.filter((id) => id !== playerId);
    } else if (this.loserIds.length < this.maxLosersAllowed) {
      this.loserIds = [...this.loserIds, playerId];
      this.winnerIds = this.winnerIds.filter((id) => id !== playerId);
      if (this.winnerId === playerId) {
        this.winnerId = null;
      }
    }
  }

  resolveFramePlayerId(player: ActiveFramePlayer): number | null {
    const rawId = player.userId ?? null;
    return typeof rawId === 'number' && Number.isFinite(rawId) ? rawId : null;
  }

  private getEligibleFramePlayerIds(): number[] {
    return this.framePlayers
      .map((player) => this.resolveFramePlayerId(player))
      .filter((playerId): playerId is number => playerId !== null);
  }

  private getNormalizedSelection(ids: number[]): number[] {
    return [...new Set(ids.map((id) => Number(id)).filter((id) => Number.isFinite(id) && id > 0))];
  }

  confirmEndFrame(): void {
    if (!this.activeFrame?.id || !this.canEndFrame() || this.isEndingFrame) {
      return;
    }

    this.isEndingFrame = true;

    const payload = this.isTeamMatch
      ? { mode: 'TEAM', winnerIds: this.winnerIds, loserIds: this.loserIds }
      : this.playerCount === 3
        ? { mode: 'SINGLE', winnerId: this.winnerId, loserIds: this.loserIds }
        : (this.playerCount === 5 || this.playerCount === 6)
          ? { mode: 'SINGLE', loserIds: this.loserIds }
          : { mode: 'SINGLE', winnerId: this.winnerId, looserId: this.looserId };

    this.http
      .post<EndFrameResponse>(`/api/frame/end/${this.activeFrame.id}`, payload)
      .subscribe({
        next: (res) => {
          this.lastEndedTable = this.activeFrame?.tableId
            ? {
                id: this.activeFrame.tableId,
                tableName: this.activeFrame.tableName,
              }
            : null;
          this.lastEndedPlayers = [...this.framePlayers];
          this.clearTimer();
          this.showEndPopup = false;
          this.billAmount = res.amount;
          this.billDuration = res.duration;
          this.activeFrame = null;
          this.players = [];
          this.framePlayers = [];
          this.timerSeconds = 0;
          this.gameMode = 'SINGLE';
          this.winnerId = null;
          this.looserId = null;
          this.winnerIds = [];
          this.loserIds = [];
          this.isEndingFrame = false;
          if (this.isPrivileged()) {
            this.loadOngoingFrames();
          }
          this.loadTables();
          this.cdr.markForCheck();
        },
        error: (err) => {
          console.error('Failed to end frame', err);
          this.isEndingFrame = false;
          alert('Unable to end frame right now');
        },
      });
  }

  closeEndPopup(): void {
    if (this.isEndingFrame) {
      return;
    }
    this.showEndPopup = false;
  }

  canRestartSameFrame(): boolean {
    return this.isPrivileged()
      && !!this.lastEndedTable?.id
      && !!this.currentUserId
      && this.billAmount !== null
      && this.lastEndedPlayers.length >= 2
      && !this.activeFrame;
  }

  startNewFrameWithSameTableAndPlayers(): void {
    if (!this.canRestartSameFrame() || this.isRestartingSameFrame) {
      return;
    }

    if (!this.lastEndedTable?.id || !this.currentUserId) {
      alert('Unable to start a new frame right now. Please refresh and try again.');
      return;
    }

    const payload: RestartFramePayload = {
      tableId: this.lastEndedTable.id,
      startedBy: this.currentUserId,
      players: this.lastEndedPlayers
        .filter((player) => player.userId !== null && player.userId !== undefined)
        .map((player) => ({
          userId: player.userId as number,
          name: player.playerName,
        })),
    };

    this.isRestartingSameFrame = true;

    this.http.post<number>('/api/frame/start', payload).subscribe({
      next: (frameId) => {
        this.billAmount = null;
        this.billDuration = null;
        this.activeFrame = {
          id: frameId,
          tableId: this.lastEndedTable?.id ?? 0,
          tableName: this.lastEndedTable?.tableName ?? '',
          startTime: new Date().toISOString(),
          status: 'STARTED',
        };
        this.players = [...this.lastEndedPlayers];
        this.framePlayers = [...this.lastEndedPlayers];
        this.showEndPopup = false;
        this.gameMode = 'SINGLE';
        this.winnerId = null;
        this.looserId = null;
        this.winnerIds = [];
        this.loserIds = [];
        this.timerSeconds = 0;
        this.isRestartingSameFrame = false;
        this.startTimerFromServerTime();
        if (this.isPrivileged()) {
          this.loadOngoingFrames();
        }
        this.loadTables();
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to start new frame with same table and players', err);
        this.isRestartingSameFrame = false;
        alert('Unable to start a new frame right now');
        this.cdr.markForCheck();
      },
    });
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
        this.currentUserId = user.id ?? null;
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
        const availableTables = res.filter((table) => table.isAvailable !== false && table.tableName !== 'Kids Ocean Dream Land');
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
