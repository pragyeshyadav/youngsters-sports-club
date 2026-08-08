import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription, finalize } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { OrganizationContextService } from '../../../core/services/organization-context.service';

interface ParentUserOption {
  id: number;
  name: string;
  email: string;
}

interface GameActivityOption {
  id: number;
  gameName: string;
  basePricePerMinute: number | string | null;
}

interface SelectedActivityItem {
  gameId: number;
  gameName: string;
  numberOfChildren: number;
  durationMinutes: number;
  ratePerMinute: number;
  totalAmount: number;
}

@Component({
  selector: 'app-play-zone-activities',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './play-zone-activities.component.html',
  styleUrl: './play-zone-activities.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlayZoneActivitiesComponent implements OnInit, OnDestroy {
  private static readonly SOFT_PLAY_ZONE_NAME = 'soft play zone';

  private readonly http = inject(HttpClient);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly authService = inject(AuthService);
  private readonly organizationContextService = inject(OrganizationContextService);
  private readonly subscriptions = new Subscription();
  private parentSearchRequestId = 0;
  private gameSearchRequestId = 0;
  private currentBranchId: number | null = null;

  @Input() createdByUserId: number | null = null;

  readonly durationOptions = [10, 15, 20, 30, 45, 50, 60, 70, 80, 90, 120];
  readonly kidsOptions = Array.from({ length: 30 }, (_, index) => index + 1);

  isExpanded = false;
  parentSearchText = '';
  parentUsers: ParentUserOption[] = [];
  selectedParentUser: ParentUserOption | null = null;
  gameSearchText = '';
  activityGames: GameActivityOption[] = [];
  selectedGame: GameActivityOption | null = null;
  selectedDuration = 10;
  selectedKidCount = 1;
  selectedActivities: SelectedActivityItem[] = [];
  isLoadingParents = false;
  isLoadingGames = false;
  isSubmitting = false;

  ngOnInit(): void {
    this.currentBranchId = this.organizationContextService.getSnapshot()?.currentBranch?.id ?? null;
    this.subscriptions.add(
      this.organizationContextService.currentBranchId$.subscribe((branchId) => {
        if (this.currentBranchId === branchId) {
          return;
        }

        this.currentBranchId = branchId;
        this.resetStateForBranchChange();
      }),
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  toggle(): void {
    this.isExpanded = !this.isExpanded;
    this.cdr.markForCheck();
  }

  searchParents(): void {
    const query = this.parentSearchText.trim();
    const requestId = ++this.parentSearchRequestId;

    if (query.length < 3) {
      this.parentUsers = [];
      this.isLoadingParents = false;
      if (!this.selectedParentUser || this.selectedParentUser.name !== this.parentSearchText) {
        this.selectedParentUser = null;
        this.selectedActivities = [];
      }
      this.cdr.markForCheck();
      return;
    }

    if (this.selectedParentUser && this.selectedParentUser.name !== query) {
      this.selectedParentUser = null;
      this.selectedActivities = [];
    }

    this.isLoadingParents = true;
    this.cdr.markForCheck();
    this.http.get<ParentUserOption[]>(
      `/api/users/search/current-branch?query=${encodeURIComponent(query)}`,
      { headers: this.buildActorHeaders() },
    ).subscribe({
      next: (users) => {
        if (requestId !== this.parentSearchRequestId) {
          return;
        }
        this.parentUsers = users;
        this.isLoadingParents = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        if (requestId !== this.parentSearchRequestId) {
          return;
        }
        console.error('Failed to search parent customers', err);
        this.parentUsers = [];
        this.isLoadingParents = false;
        this.cdr.markForCheck();
      },
    });
  }

  selectParent(user: ParentUserOption): void {
    this.selectedParentUser = user;
    this.parentSearchText = user.name;
    this.parentUsers = [];
    this.selectedActivities = [];
    this.resetSelectedGame();
    this.cdr.markForCheck();
  }

  searchGames(): void {
    const query = this.gameSearchText.trim();
    const requestId = ++this.gameSearchRequestId;

    if (query.length < 3) {
      this.activityGames = [];
      this.isLoadingGames = false;
      if (!this.selectedGame || this.selectedGame.gameName !== this.gameSearchText) {
        this.selectedGame = null;
      }
      this.cdr.markForCheck();
      return;
    }

    if (this.selectedGame && this.selectedGame.gameName !== query) {
      this.selectedGame = null;
    }

    this.isLoadingGames = true;
    this.cdr.markForCheck();
    this.http.get<GameActivityOption[]>(
      `/api/play-zone-activities/games/search?query=${encodeURIComponent(query)}`,
      { headers: this.buildActorHeaders() },
    ).subscribe({
      next: (games) => {
        if (requestId !== this.gameSearchRequestId) {
          return;
        }
        this.activityGames = games;
        this.isLoadingGames = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        if (requestId !== this.gameSearchRequestId) {
          return;
        }
        console.error('Failed to search game activities', err);
        this.activityGames = [];
        this.isLoadingGames = false;
        this.cdr.markForCheck();
      },
    });
  }

  selectGame(game: GameActivityOption): void {
    this.selectedGame = game;
    this.gameSearchText = game.gameName;
    this.activityGames = [];
    this.isLoadingGames = false;
    this.gameSearchRequestId++;
    if (!this.isSoftPlayZone(game)) {
      this.selectedKidCount = 1;
    }
    this.cdr.markForCheck();
  }

  addActivity(): void {
    if (!this.selectedParentUser || !this.selectedGame) {
      return;
    }

    const ratePerMinute = this.toNumber(this.selectedGame.basePricePerMinute);
    const numberOfChildren = this.isSoftPlayZone(this.selectedGame) ? this.selectedKidCount : 1;
    const totalAmount = ratePerMinute * this.selectedDuration * numberOfChildren;

    this.selectedActivities = [
      ...this.selectedActivities,
      {
        gameId: this.selectedGame.id,
        gameName: this.selectedGame.gameName,
        numberOfChildren,
        durationMinutes: this.selectedDuration,
        ratePerMinute,
        totalAmount,
      },
    ];

    this.resetSelectedGame();
    this.cdr.markForCheck();
  }

  removeActivity(index: number): void {
    this.selectedActivities = this.selectedActivities.filter((_, currentIndex) => currentIndex !== index);
    this.cdr.markForCheck();
  }

  getActivitiesTotal(): number {
    return this.selectedActivities.reduce((sum, item) => sum + item.totalAmount, 0);
  }

  submitActivities(): void {
    if (!this.selectedParentUser || !this.createdByUserId || this.selectedActivities.length === 0 || this.isSubmitting) {
      return;
    }

    this.isSubmitting = true;
    this.cdr.markForCheck();
    this.http.post(
      '/api/play-zone-activities/order',
      {
        parentUserId: this.selectedParentUser.id,
        createdBy: this.createdByUserId,
        activities: this.selectedActivities.map((activity) => ({
          gameId: activity.gameId,
          numberOfChildren: activity.numberOfChildren,
          durationMinutes: activity.durationMinutes,
        })),
      },
      { headers: this.buildActorHeaders() },
    ).pipe(
      finalize(() => {
        this.isSubmitting = false;
        this.cdr.markForCheck();
      }),
    ).subscribe({
      next: () => {
        this.selectedActivities = [];
        this.selectedParentUser = null;
        this.parentSearchText = '';
        this.parentUsers = [];
        this.resetSelectedGame();
        this.parentSearchRequestId++;
        this.cdr.detectChanges();
        alert('Play zone activities saved successfully');
      },
      error: (err) => {
        console.error('Failed to submit play zone activities', err);
        this.cdr.detectChanges();
        alert(err?.error?.message || 'Unable to save play zone activities right now');
      },
    });
  }

  isSoftPlaySelected(): boolean {
    return this.isSoftPlayZone(this.selectedGame);
  }

  formatActivityMeta(item: SelectedActivityItem): string {
    const childrenText = item.numberOfChildren > 1 ? ` • ${item.numberOfChildren} kids` : '';
    return `${item.durationMinutes} mins${childrenText} • ₹${item.ratePerMinute}/min`;
  }

  private resetSelectedGame(): void {
    this.selectedGame = null;
    this.gameSearchText = '';
    this.activityGames = [];
    this.selectedDuration = 10;
    this.selectedKidCount = 1;
    this.isLoadingGames = false;
    this.gameSearchRequestId++;
  }

  private isSoftPlayZone(game: GameActivityOption | null): boolean {
    return game?.gameName?.trim().toLowerCase() === PlayZoneActivitiesComponent.SOFT_PLAY_ZONE_NAME;
  }

  private toNumber(value: number | string | null): number {
    if (value === null || value === undefined || value === '') {
      return 0;
    }
    const parsed = typeof value === 'number' ? value : Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  private resetStateForBranchChange(): void {
    this.parentSearchRequestId++;
    this.gameSearchRequestId++;
    this.parentSearchText = '';
    this.parentUsers = [];
    this.selectedParentUser = null;
    this.selectedActivities = [];
    this.isLoadingParents = false;
    this.isLoadingGames = false;
    this.isSubmitting = false;
    this.resetSelectedGame();
    this.cdr.markForCheck();
  }

  private buildActorHeaders(): HttpHeaders {
    const actorEmail = this.authService.getSnapshot()?.user.email ?? this.getStoredUserEmail();
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
