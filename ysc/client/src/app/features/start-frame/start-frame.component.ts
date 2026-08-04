import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Observable, Subscription } from 'rxjs';
import { AuthUser } from '../../core/models/auth.models';
import { AuthService } from '../../core/services/auth.service';
import { OrganizationContextService } from '../../core/services/organization-context.service';
import { BrandTitleComponent } from '../../shared/components/brand-title/brand-title.component';
import { ClubLogoComponent } from '../../shared/components/club-logo/club-logo.component';

interface SnookerTable {
  id: number;
  tableName: string;
  ratePerMinute?: number;
  branchId?: number | null;
  branchName?: string | null;
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
  email?: string;
}

interface EndFrameResponse {
  duration: number;
  amount: number;
  frameId: number;
  tableId: number;
}

interface ExistingFrame {
  id: number;
  tableId: number | null;
  tableName: string | null;
  startTime: string;
  status: string;
  endTime?: string | null;
}

interface ExistingFrameResponse {
  frame: ExistingFrame;
  players: FramePlayerOption[];
}

type FrameGameMode = 'SINGLE' | 'TEAM';

@Component({
  selector: 'app-start-frame',
  standalone: true,
  imports: [CommonModule, FormsModule, BrandTitleComponent, ClubLogoComponent],
  templateUrl: './start-frame.component.html',
  styleUrl: './start-frame.component.scss',
})
export class StartFrameComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly organizationContextService = inject(OrganizationContextService);

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
  gameMode: FrameGameMode = 'SINGLE';
  winnerId: number | null = null;
  looserId: number | null = null;
  winnerIds: number[] = [];
  loserIds: number[] = [];
  framePlayers: FramePlayerOption[] = [];
  billAmount: number | null = null;
  billDuration: number | null = null;
  viewMode: 'start' | 'manage' = 'start';
  backRoute = '/snooker-frame';
  isStartingFrame = false;
  isRestartingSameFrame = false;
  isOpeningEndPopup = false;
  isEndingFrame = false;
  private currentBranchId: number | null = null;
  private readonly subscriptions = new Subscription();
  private timerInterval: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    this.subscribeToBranchChanges();
    const state = history.state as { table?: SnookerTable; frameId?: number; source?: string } | undefined;
    const frameId = state?.frameId;
    this.backRoute = state?.source === 'manager-portal' ? '/managers-portal' : '/snooker-frame';

    if (frameId) {
      this.viewMode = 'manage';
      this.loadExistingFrame(frameId);
    } else {
      this.selectedTable = state?.table ?? null;

      if (!this.selectedTable) {
        void this.router.navigate(['/snooker-frame']);
        return;
      }

      const currentBranchId = this.organizationContextService.getSnapshot()?.currentBranch?.id ?? null;
      if (currentBranchId && this.selectedTable.branchId && this.selectedTable.branchId !== currentBranchId) {
        this.selectedTable = null;
        void this.router.navigate(['/snooker-frame']);
        return;
      }
    }

    this.loadCurrentUser();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
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
    if (this.isStartingFrame || this.isRestartingSameFrame || this.frameStarted) {
      return;
    }

    if (!this.selectedPlayers || this.selectedPlayers.length < 2) {
      alert('Please select at least 2 players to start a frame');
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

    this.isStartingFrame = true;
    const headers = this.buildActorHeaders();

    this.http.post<number>('/api/frame/start', request, { headers }).subscribe({
      next: (frameId) => {
        this.frameStarted = true;
        this.frameId = frameId;
        this.billAmount = null;
        this.billDuration = null;
        this.isStartingFrame = false;
        this.startTimer();
      },
      error: (err) => {
        console.error('Failed to start frame', err);
        this.isStartingFrame = false;
        alert('Unable to start frame right now');
      },
    });
  }

  canRestartSameFrame(): boolean {
    return this.viewMode === 'manage'
      && !!this.selectedTable?.id
      && !!this.authUser?.id
      && !this.frameStarted
      && this.billAmount !== null
      && this.selectedPlayers.length >= 2;
  }

  startNewFrameWithSameTableAndPlayers(): void {
    if (!this.canRestartSameFrame() || this.isRestartingSameFrame) {
      return;
    }

    const tableId = this.selectedTable?.id;
    const startedBy = this.authUser?.id;
    if (!tableId || !startedBy) {
      alert('Unable to start a new frame right now. Please refresh and try again.');
      return;
    }

    const request: StartFramePayload = {
      tableId,
      startedBy,
      players: this.selectedPlayers.map((player) => ({
        userId: player.id,
        name: player.name,
      })),
    };

    this.isRestartingSameFrame = true;
    const headers = this.buildActorHeaders();

    this.http.post<number>('/api/frame/start', request, { headers }).subscribe({
      next: (newFrameId) => {
        this.frameStarted = true;
        this.frameId = newFrameId;
        this.billAmount = null;
        this.billDuration = null;
        this.gameMode = 'SINGLE';
        this.winnerId = null;
        this.looserId = null;
        this.winnerIds = [];
        this.loserIds = [];
        this.framePlayers = this.selectedPlayers.map((player) => ({
          userId: player.id,
          id: player.id,
          name: player.name,
          playerName: player.name,
          email: player.email,
        }));
        this.searchText = '';
        this.players = [];
        this.isRestartingSameFrame = false;
        this.startTimer();
      },
      error: (err) => {
        console.error('Failed to restart frame with same table and players', err);
        this.isRestartingSameFrame = false;
        alert('Unable to start a new frame right now');
      },
    });
  }

  openEndPopup(): void {
    if (!this.frameId || this.isOpeningEndPopup || this.isEndingFrame) {
      return;
    }

    this.showEndPopup = true;
    this.gameMode = 'SINGLE';
    this.winnerId = null;
    this.looserId = null;
    this.winnerIds = [];
    this.loserIds = [];

    if (
      this.selectedPlayers.length > 0 &&
      this.selectedPlayers.every((player) => player.id !== null && player.id !== undefined && player.id > 0)
    ) {
      this.framePlayers = this.selectedPlayers;
      return;
    }

    this.isOpeningEndPopup = true;
    const headers = this.buildActorHeaders();

    this.http.get<FramePlayerOption[]>(`/api/frame/${this.frameId}/players`, { headers }).subscribe({
      next: (res) => {
        this.framePlayers = res;
        this.isOpeningEndPopup = false;
      },
      error: (err) => {
        console.error('Failed to load frame players', err);
        this.framePlayers = [];
        this.isOpeningEndPopup = false;
      },
    });
  }

  get isTeamMatch(): boolean {
    return this.canUseTeamMode && this.gameMode === 'TEAM';
  }

  get canUseTeamMode(): boolean {
    return this.framePlayers.length === 4;
  }

  get playerCount(): number {
    return this.framePlayers.length;
  }

  get supportsDynamicLosers(): boolean {
    return this.playerCount === 3 || this.playerCount === 5 || this.playerCount === 6;
  }

  get maxLosersAllowed(): number {
    if (this.isTeamMatch) {
      return 2;
    }
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
      if (winnerIds.length !== 2 || loserIds.length !== 2) return false;
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
    if (!playerId) return;
    if (this.winnerIds.includes(playerId)) {
      this.winnerIds = this.winnerIds.filter(id => id !== playerId);
    } else if (this.winnerIds.length < 2) {
      this.winnerIds = [...this.winnerIds, playerId];
      this.loserIds = this.loserIds.filter(id => id !== playerId);
    }
  }

  toggleLoser(playerId: number | undefined | null): void {
    if (!playerId) return;
    if (this.loserIds.includes(playerId)) {
      this.loserIds = this.loserIds.filter(id => id !== playerId);
    } else if (this.loserIds.length < this.maxLosersAllowed) {
      this.loserIds = [...this.loserIds, playerId];
      this.winnerIds = this.winnerIds.filter(id => id !== playerId);
      if (this.winnerId === playerId) {
        this.winnerId = null;
      }
    }
  }

  resolveFramePlayerId(player: FramePlayerOption): number | null {
    const rawId = player.userId ?? player.id ?? null;
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
    if (!this.frameId || !this.canEndFrame() || this.isEndingFrame) {
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
    const headers = this.buildActorHeaders();

    this.http
      .post<EndFrameResponse>(`/api/frame/end/${this.frameId}`, payload, { headers })
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
          if (this.viewMode === 'start') {
            this.selectedPlayers = [];
            this.framePlayers = [];
          }
          this.winnerId = null;
          this.looserId = null;
          this.winnerIds = [];
          this.loserIds = [];
          this.gameMode = 'SINGLE';
          this.isEndingFrame = false;
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

  goBack(): void {
    if (this.frameStarted || this.isStartingFrame || this.isEndingFrame) {
      return;
    }
    void this.router.navigate([this.backRoute]);
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

  private startTimerFromServerTime(startTimeValue: string): void {
    this.clearTimer();

    const startTime = new Date(startTimeValue).getTime();
    this.seconds = Math.floor((Date.now() - startTime) / 1000);

    this.timerInterval = setInterval(() => {
      this.seconds = Math.floor((Date.now() - startTime) / 1000);
    }, 1000);
  }

  private clearTimer(): void {
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
      this.timerInterval = null;
    }
  }

  private buildActorHeaders(): HttpHeaders {
    const actorEmail = this.auth.getSnapshot()?.user.email?.trim();
    return actorEmail ? new HttpHeaders({ 'X-User-Email': actorEmail }) : new HttpHeaders();
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

  private loadExistingFrame(frameId: number): void {
    const headers = this.buildActorHeaders();
    this.http.get<ExistingFrameResponse | null>(`/api/frame/${frameId}`, { headers }).subscribe({
      next: (res) => {
        if (!res?.frame || res.frame.status !== 'STARTED' || res.frame.endTime) {
          alert('This frame is no longer active');
          void this.router.navigate(['/managers-portal']);
          return;
        }

        this.selectedTable = res.frame.tableId
          ? {
              id: res.frame.tableId,
              tableName: res.frame.tableName ?? `Table #${res.frame.tableId}`,
            }
          : null;
        this.frameId = res.frame.id;
        this.frameStarted = true;
        this.selectedPlayers = (res.players ?? []).map((player) => ({
          id: player.userId ?? player.id ?? 0,
          name: player.name ?? player.playerName ?? 'Player',
          email: player.email ?? '',
        }));
        this.framePlayers = res.players ?? [];
        this.startTimerFromServerTime(res.frame.startTime);
      },
      error: (err) => {
        console.error('Failed to load frame details', err);
        alert('Unable to load frame details right now');
        void this.router.navigate(['/managers-portal']);
      },
    });
  }

  private subscribeToBranchChanges(): void {
    this.currentBranchId = this.organizationContextService.getSnapshot()?.currentBranch?.id ?? null;

    this.subscriptions.add(
      this.organizationContextService.context$.subscribe((context) => {
        const nextBranchId = context?.currentBranch?.id ?? null;
        if (this.currentBranchId === nextBranchId) {
          return;
        }

        this.currentBranchId = nextBranchId;
        if (this.viewMode === 'manage') {
          this.resetSelectionForBranchChange();
          void this.router.navigate(['/snooker-frame']);
          return;
        }

        if (!nextBranchId || !this.selectedTable?.branchId) {
          return;
        }

        if (this.selectedTable.branchId !== nextBranchId) {
          this.resetSelectionForBranchChange();
          void this.router.navigate(['/snooker-frame']);
        }
      }),
    );
  }

  private resetSelectionForBranchChange(): void {
    this.clearTimer();
    this.selectedTable = null;
    this.selectedPlayers = [];
    this.players = [];
    this.searchText = '';
    this.framePlayers = [];
    this.frameStarted = false;
    this.frameId = null;
    this.showEndPopup = false;
    this.billAmount = null;
    this.billDuration = null;
    this.gameMode = 'SINGLE';
    this.winnerId = null;
    this.looserId = null;
    this.winnerIds = [];
    this.loserIds = [];
    this.isStartingFrame = false;
    this.isRestartingSameFrame = false;
    this.isOpeningEndPopup = false;
    this.isEndingFrame = false;
  }
}
