import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

export interface PlayerPerformanceView {
  player: { displayName: string; profileImageUrl: string | null; totalFrames: number; wins: number; losses: number; winRate: number; recentForm: string[] };
  competitorComparison: { eligible: boolean; minimumFramesRequired: number; players: { displayName: string; winRate: number; totalFrames: number; currentPlayer: boolean }[]; message: string | null };
  lastUpdatedAt: string;
}

export interface PlayerPerformanceLevel {
  level: 'ROOKIE' | 'AMATEUR' | 'PRO' | 'ELITE' | 'LEGEND';
  label: string;
  subtitle?: string;
  icon: string;
  cssClass: string;
}

export function classifyPlayerPerformanceLevel(winRate: number | null | undefined): PlayerPerformanceLevel | null {
  if (typeof winRate !== 'number' || !Number.isFinite(winRate) || winRate < 0 || winRate > 100) {
    return null;
  }

  if (winRate < 25) {
    return { level: 'ROOKIE', label: 'Rookie', subtitle: 'Beginner', icon: '🎱', cssClass: 'player-level-rookie' };
  }
  if (winRate < 50) {
    return { level: 'AMATEUR', label: 'Amateur', icon: '⚔️', cssClass: 'player-level-amateur' };
  }
  if (winRate < 75) {
    return { level: 'PRO', label: 'Pro', icon: '🥇', cssClass: 'player-level-pro' };
  }
  if (winRate < 90) {
    return { level: 'ELITE', label: 'Elite', icon: '💎', cssClass: 'player-level-elite' };
  }
  return { level: 'LEGEND', label: 'Legend', icon: '👑', cssClass: 'player-level-legend' };
}

@Component({
  selector: 'app-player-performance-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './player-performance-card.component.html',
  styleUrl: './player-performance-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlayerPerformanceCardComponent {
  @Input({ required: true }) performance!: PlayerPerformanceView;

  get playerLevel(): PlayerPerformanceLevel | null {
    return classifyPlayerPerformanceLevel(this.performance?.player?.winRate);
  }

  get winRateClass(): string {
    if (!this.playerLevel || this.performance.player.totalFrames === 0) {
      return 'win-rate--neutral';
    }
    return `win-rate--${this.playerLevel.level.toLowerCase()}`;
  }

  get displayedWinRate(): string {
    if (!this.playerLevel || this.performance.player.totalFrames === 0) {
      return '—';
    }
    return `${this.performance.player.winRate.toFixed(1)}%`;
  }
}
