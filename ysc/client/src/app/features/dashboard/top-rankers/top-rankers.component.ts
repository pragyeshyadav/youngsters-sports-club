import { Component, inject, OnDestroy, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { OrganizationContextService } from '../../../core/services/organization-context.service';

interface TopPlayer {
  userId?: number;
  name: string;
  wins: number;
  branchId?: number | null;
  branchName?: string | null;
  year?: number;
  month?: number;
}

@Component({
  selector: 'app-top-rankers',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './top-rankers.component.html',
  styleUrls: ['./top-rankers.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopRankersComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly authService = inject(AuthService);
  private readonly organizationContextService = inject(OrganizationContextService);
  private readonly subscriptions = new Subscription();

  players: TopPlayer[] = [];
  isLoading = true;
  private currentBranchId: number | null = null;
  private latestRequestId = 0;

  ngOnInit() {
    this.subscribeToBranchChanges();
  }

  ngOnDestroy() {
    this.subscriptions.unsubscribe();
  }

  fetchTopPlayers() {
    const actorEmail = this.authService.getSnapshot()?.user.email?.trim();
    if (!actorEmail || !this.currentBranchId) {
      this.players = [];
      this.isLoading = false;
      this.cdr.markForCheck();
      return;
    }

    this.isLoading = true;
    const requestId = ++this.latestRequestId;
    const headers = new HttpHeaders({
      'X-User-Email': actorEmail,
    });

    this.http.get<TopPlayer[]>('/api/leaderboard/top-players', { headers }).subscribe({
      next: (res) => {
        if (requestId !== this.latestRequestId) {
          return;
        }
        this.players = res;
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        if (requestId !== this.latestRequestId) {
          return;
        }
        console.error('Failed to fetch top players', err);
        this.players = [];
        this.isLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  private subscribeToBranchChanges(): void {
    const snapshot = this.organizationContextService.getSnapshot();
    if (snapshot?.currentBranch?.id) {
      this.currentBranchId = snapshot.currentBranch.id;
      this.fetchTopPlayers();
    } else {
      this.players = [];
      this.isLoading = false;
      this.cdr.markForCheck();
    }

    this.subscriptions.add(
      this.organizationContextService.context$.subscribe((context) => {
        const nextBranchId = context?.currentBranch?.id ?? null;
        if (this.currentBranchId === nextBranchId) {
          return;
        }

        this.currentBranchId = nextBranchId;
        this.players = [];
        this.isLoading = true;
        this.cdr.markForCheck();

        if (nextBranchId) {
          this.fetchTopPlayers();
          return;
        }

        this.isLoading = false;
        this.cdr.markForCheck();
      }),
    );
  }
}
