import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { BrandTitleComponent } from '../../shared/components/brand-title/brand-title.component';
import { ClubLogoComponent } from '../../shared/components/club-logo/club-logo.component';

interface OngoingFrame {
  id: number;
  tableId: number | null;
  tableName: string | null;
  startTime: string;
  status: string;
  startedBy: string | null;
  players: string[];
}

interface CompletedFrame {
  id: number;
  winnerName: string | null;
  looserName: string | null;
  startTime: string;
  endTime: string;
  durationMinutes: number | null;
  totalAmount: number | string | null;
  paymentDue: number | string | null;
}

@Component({
  selector: 'app-managers-portal',
  standalone: true,
  imports: [CommonModule, BrandTitleComponent, ClubLogoComponent],
  templateUrl: './managers-portal.component.html',
  styleUrl: './managers-portal.component.scss',
})
export class ManagersPortalComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private resizeHandler: (() => void) | null = null;

  isOngoingExpanded = false;
  ongoingFrames: OngoingFrame[] = [];
  isCompletedExpanded = false;
  completedFrames: CompletedFrame[] = [];
  isMobile = false;
  isLoadingOngoing = false;
  isLoadingCompleted = false;

  ngOnInit(): void {
    this.updateViewportState();
    this.resizeHandler = () => this.updateViewportState();
    window.addEventListener('resize', this.resizeHandler);
  }

  ngOnDestroy(): void {
    if (this.resizeHandler) {
      window.removeEventListener('resize', this.resizeHandler);
      this.resizeHandler = null;
    }
  }

  toggleOngoing(): void {
    this.isOngoingExpanded = !this.isOngoingExpanded;

    if (this.isOngoingExpanded && this.ongoingFrames.length === 0) {
      this.loadOngoingFrames();
    }
  }

  loadOngoingFrames(): void {
    this.isLoadingOngoing = true;

    this.http.get<OngoingFrame[]>('/api/frame/ongoing/today').subscribe({
      next: (frames) => {
        this.ongoingFrames = frames;
        this.isLoadingOngoing = false;
      },
      error: (err) => {
        console.error('Failed to load ongoing frames', err);
        this.ongoingFrames = [];
        this.isLoadingOngoing = false;
      },
    });
  }

  toggleCompleted(): void {
    this.isCompletedExpanded = !this.isCompletedExpanded;

    if (this.isCompletedExpanded && this.completedFrames.length === 0) {
      this.loadCompletedFrames();
    }
  }

  loadCompletedFrames(): void {
    this.isLoadingCompleted = true;

    this.http.get<CompletedFrame[]>('/api/frame/completed/today').subscribe({
      next: (frames) => {
        this.completedFrames = frames;
        this.isLoadingCompleted = false;
      },
      error: (err) => {
        console.error('Failed to load completed frames', err);
        this.completedFrames = [];
        this.isLoadingCompleted = false;
      },
    });
  }

  endFrame(frameId: number): void {
    void this.router.navigate(['/start-frame'], { state: { frameId, source: 'manager-portal' } });
  }

  rejectFrame(frameId: number): void {
    this.http.post(`/api/frame/reject/${frameId}`, {}).subscribe({
      next: () => {
        this.loadOngoingFrames();
      },
      error: (err) => {
        console.error('Failed to reject frame', err);
        alert('Unable to reject frame right now');
      },
    });
  }

  goToSettlement(): void {
    void this.router.navigate(['/payment-settlement']);
  }

  getPaymentRowClass(frame: CompletedFrame): string {
    const paymentDue = this.toNumber(frame.paymentDue);
    const totalAmount = this.toNumber(frame.totalAmount);

    if (paymentDue <= 0) {
      return 'paid-row';
    }

    if (totalAmount > 0 && paymentDue > 0 && paymentDue < totalAmount) {
      return 'partial-row';
    }

    return 'due-row';
  }

  private updateViewportState(): void {
    this.isMobile = window.innerWidth < 768;
  }

  private toNumber(value: number | string | null): number {
    if (value === null || value === undefined || value === '') {
      return 0;
    }

    return typeof value === 'number' ? value : Number(value);
  }
}
