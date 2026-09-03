import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  classifyPlayerPerformanceLevel,
  PlayerPerformanceCardComponent,
  PlayerPerformanceView,
} from './player-performance-card.component';

describe('PlayerPerformanceCardComponent', () => {
  let fixture: ComponentFixture<PlayerPerformanceCardComponent>;
  let component: PlayerPerformanceCardComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PlayerPerformanceCardComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(PlayerPerformanceCardComponent);
    component = fixture.componentInstance;
  });

  it('classifies every win-rate boundary with the approved level and styling', () => {
    const cases = [
      [0, 'Rookie', '🎱', 'player-level-rookie'],
      [10, 'Rookie', '🎱', 'player-level-rookie'],
      [24.9, 'Rookie', '🎱', 'player-level-rookie'],
      [25, 'Amateur', '⚔️', 'player-level-amateur'],
      [46.4, 'Amateur', '⚔️', 'player-level-amateur'],
      [49.9, 'Amateur', '⚔️', 'player-level-amateur'],
      [50, 'Pro', '🥇', 'player-level-pro'],
      [60, 'Pro', '🥇', 'player-level-pro'],
      [74.9, 'Pro', '🥇', 'player-level-pro'],
      [75, 'Elite', '💎', 'player-level-elite'],
      [82.5, 'Elite', '💎', 'player-level-elite'],
      [89.9, 'Elite', '💎', 'player-level-elite'],
      [90, 'Legend', '👑', 'player-level-legend'],
      [95, 'Legend', '👑', 'player-level-legend'],
      [100, 'Legend', '👑', 'player-level-legend'],
    ] as const;

    for (const [winRate, label, icon, cssClass] of cases) {
      const level = classifyPlayerPerformanceLevel(winRate);
      expect(level).withContext(`win rate ${winRate}`).toEqual(jasmine.objectContaining({ label, icon, cssClass }));
    }
  });

  it('includes the Beginner subtitle only for Rookie', () => {
    expect(classifyPlayerPerformanceLevel(0)).toEqual(
      jasmine.objectContaining({ label: 'Rookie', subtitle: 'Beginner' }),
    );
    expect(classifyPlayerPerformanceLevel(25)?.subtitle).toBeUndefined();
  });

  it('returns no classification for invalid win rates', () => {
    expect(classifyPlayerPerformanceLevel(null)).toBeNull();
    expect(classifyPlayerPerformanceLevel(undefined)).toBeNull();
    expect(classifyPlayerPerformanceLevel(Number.NaN)).toBeNull();
    expect(classifyPlayerPerformanceLevel(Number.POSITIVE_INFINITY)).toBeNull();
    expect(classifyPlayerPerformanceLevel(-1)).toBeNull();
    expect(classifyPlayerPerformanceLevel(101)).toBeNull();
  });

  it('renders the same level badge and win-rate styling used by every card consumer', () => {
    component.performance = buildPerformance(46.4, 56);
    fixture.detectChanges();

    const badge: HTMLElement = fixture.nativeElement.querySelector('.player-level-badge');
    const winRate: HTMLElement = fixture.nativeElement.querySelector('.win-rate');

    expect(badge.textContent).toContain('⚔️ Amateur');
    expect(badge.classList).toContain('player-level-amateur');
    expect(winRate.classList).toContain('win-rate--amateur');
    expect(winRate.textContent).toContain('46.4%');
  });

  it('keeps zero-frame players neutral while showing the Rookie starting badge', () => {
    component.performance = buildPerformance(0, 0);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.player-level-badge').textContent).toContain('🎱 Rookie');
    expect(fixture.nativeElement.querySelector('.win-rate').classList).toContain('win-rate--neutral');
    expect(fixture.nativeElement.querySelector('.win-rate strong').textContent.trim()).toBe('—');
  });

  it('keeps corrupt win-rate values safe and neutral', () => {
    component.performance = buildPerformance(Number.NaN, 10);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.player-level-badge')).toBeNull();
    expect(fixture.nativeElement.querySelector('.win-rate').classList).toContain('win-rate--neutral');
    expect(fixture.nativeElement.querySelector('.win-rate strong').textContent.trim()).toBe('—');
  });

  function buildPerformance(winRate: number, totalFrames: number): PlayerPerformanceView {
    return {
      player: {
        displayName: 'Pragyesh Yadav',
        profileImageUrl: null,
        totalFrames,
        wins: 26,
        losses: 30,
        winRate,
        recentForm: [],
      },
      competitorComparison: {
        eligible: false,
        minimumFramesRequired: 10,
        players: [],
        message: null,
      },
      lastUpdatedAt: '2026-09-03T11:30:00Z',
    };
  }
});
