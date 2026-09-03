import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { PlayerPerformanceCardComponent, PlayerPerformanceView } from '../../../shared/components/player-performance-card/player-performance-card.component';

type PerformanceResponse = PlayerPerformanceView;

@Component({
  selector: 'app-player-performance',
  standalone: true,
  imports: [CommonModule, PlayerPerformanceCardComponent],
  templateUrl: './player-performance.component.html',
  styleUrl: './player-performance.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlayerPerformanceComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly cdr = inject(ChangeDetectorRef);

  performance: PerformanceResponse | null = null;
  isLoading = true;
  hasError = false;

  ngOnInit(): void {
    this.http.get<PerformanceResponse>('/api/player/performance').subscribe({
      next: (response) => {
        this.performance = response;
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (error) => {
        console.error('Failed to load player performance', error);
        this.hasError = true;
        this.isLoading = false;
        this.cdr.markForCheck();
      },
    });
  }
}
