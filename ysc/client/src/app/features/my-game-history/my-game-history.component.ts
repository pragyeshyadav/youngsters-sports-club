import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { BrandTitleComponent } from '../../shared/components/brand-title/brand-title.component';
import { ClubLogoComponent } from '../../shared/components/club-logo/club-logo.component';
import { AuthService } from '../../core/services/auth.service';

interface BackendUser {
  id: number;
  email: string;
}

interface GameHistoryRow {
  frameId: number;
  winnerName?: string | null;
  looserName?: string | null;
  startTime: string;
  endTime?: string | null;
  duration?: number | null;
  amount?: number | null;
  paymentDue?: number | null;
}

@Component({
  selector: 'app-my-game-history',
  standalone: true,
  imports: [CommonModule, BrandTitleComponent, ClubLogoComponent],
  templateUrl: './my-game-history.component.html',
  styleUrl: './my-game-history.component.scss',
})
export class MyGameHistoryComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly handleResize = () => {
    this.isMobile = window.innerWidth < 768;
  };

  history: GameHistoryRow[] = [];
  isMobile = false;
  totalDue = 0;

  ngOnInit(): void {
    this.isMobile = window.innerWidth < 768;
    window.addEventListener('resize', this.handleResize);

    const email = this.auth.getSnapshot()?.user.email;
    if (!email) {
      return;
    }

    this.http.get<BackendUser>(`/api/user?email=${encodeURIComponent(email)}`).subscribe({
      next: (user) => {
        this.http.get<number>(`/api/frame/total-due?userId=${user.id}`).subscribe({
          next: (res) => {
            this.totalDue = res || 0;
          },
          error: (err) => {
            console.error('Failed to load total due', err);
            this.totalDue = 0;
          },
        });

        this.http.get<GameHistoryRow[]>(`/api/frame/history?userId=${user.id}`).subscribe({
          next: (res) => {
            this.history = res;
          },
          error: (err) => {
            console.error('Failed to load game history', err);
            this.history = [];
          },
        });
      },
      error: (err) => {
        console.error('Failed to load current user', err);
      },
    });
  }

  ngOnDestroy(): void {
    window.removeEventListener('resize', this.handleResize);
  }

  getRowClass(frame: GameHistoryRow): string {
    if (!frame.endTime) {
      return 'running-row';
    }
    if ((frame.paymentDue ?? 0) > 0) {
      return 'due-row';
    }
    return '';
  }
}
