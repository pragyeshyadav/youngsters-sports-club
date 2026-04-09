import { Component, inject, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';

interface TopPlayer {
  name: string;
  wins: number;
}

@Component({
  selector: 'app-top-rankers',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './top-rankers.component.html',
  styleUrls: ['./top-rankers.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopRankersComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly cdr = inject(ChangeDetectorRef);

  players: TopPlayer[] = [];
  isLoading = true;

  ngOnInit() {
    this.fetchTopPlayers();
  }

  fetchTopPlayers() {
    this.isLoading = true;
    this.http.get<TopPlayer[]>('/api/leaderboard/top-players').subscribe({
      next: (res) => {
        this.players = res;
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to fetch top players', err);
        this.isLoading = false;
        this.cdr.markForCheck();
      }
    });
  }
}
