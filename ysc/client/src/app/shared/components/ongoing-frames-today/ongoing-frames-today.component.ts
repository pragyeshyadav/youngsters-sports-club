import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { OrganizationContextService } from '../../../core/services/organization-context.service';

interface OngoingFrame {
  id: number;
  tableId: number | null;
  tableName: string | null;
  startTime: string;
  status: string;
  startedBy: string | null;
  players: string[];
}

@Component({
  selector: 'app-ongoing-frames-today',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './ongoing-frames-today.component.html',
  styleUrl: './ongoing-frames-today.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OngoingFramesTodayComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);
  private readonly organizationContextService = inject(OrganizationContextService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly subscriptions = new Subscription();

  isExpanded = false;
  ongoingFrames: OngoingFrame[] = [];
  isLoading = false;
  isMobile = false;
  private currentBranchId: number | null = null;
  private branchStateVersion = 0;
  private resizeHandler: (() => void) | null = null;

  ngOnInit(): void {
    this.updateViewportState();
    this.resizeHandler = () => this.updateViewportState();
    window.addEventListener('resize', this.resizeHandler);
    this.bindOrganizationContext();
  }

  ngOnDestroy(): void {
    if (this.resizeHandler) {
      window.removeEventListener('resize', this.resizeHandler);
      this.resizeHandler = null;
    }
    this.subscriptions.unsubscribe();
  }

  toggleExpand(): void {
    this.isExpanded = !this.isExpanded;
    if (this.isExpanded && this.ongoingFrames.length === 0) {
      this.loadOngoingFrames();
    }
  }

  endFrame(frameId: number): void {
    void this.router.navigate(['/start-frame'], { state: { frameId, source: 'manager-portal' } });
  }

  rejectFrame(frameId: number): void {
    this.http.post(`/api/frame/reject/${frameId}`, {}).subscribe({
      next: () => this.loadOngoingFrames(),
      error: (err) => {
        console.error('Failed to reject frame', err);
        alert('Unable to reject frame right now');
      },
    });
  }

  private bindOrganizationContext(): void {
    this.currentBranchId = this.organizationContextService.getSnapshot()?.currentBranch?.id ?? null;
    this.subscriptions.add(
      this.organizationContextService.context$.subscribe((context) => {
        const nextBranchId = context?.currentBranch?.id ?? null;
        if (this.currentBranchId === nextBranchId) {
          return;
        }

        this.currentBranchId = nextBranchId;
        this.branchStateVersion++;
        this.ongoingFrames = [];
        this.isLoading = false;
        if (nextBranchId && this.isExpanded) {
          this.loadOngoingFrames();
        }
        this.cdr.markForCheck();
      }),
    );
  }

  private loadOngoingFrames(): void {
    const actorEmail = this.authService.getSnapshot()?.user.email ?? this.getStoredUserEmail();
    if (!actorEmail || !this.currentBranchId) {
      this.ongoingFrames = [];
      this.isLoading = false;
      this.cdr.markForCheck();
      return;
    }

    this.isLoading = true;
    const requestVersion = this.branchStateVersion;
    const headers = new HttpHeaders({ 'X-User-Email': actorEmail.trim() });
    this.http.get<OngoingFrame[]>('/api/frame/ongoing/today', { headers }).subscribe({
      next: (frames) => {
        if (requestVersion !== this.branchStateVersion) {
          return;
        }
        this.ongoingFrames = frames ?? [];
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        if (requestVersion !== this.branchStateVersion) {
          return;
        }
        console.error('Failed to load ongoing frames', err);
        this.ongoingFrames = [];
        this.isLoading = false;
        this.cdr.markForCheck();
      },
    });
  }

  private updateViewportState(): void {
    this.isMobile = window.innerWidth < 768;
    this.cdr.markForCheck();
  }

  private getStoredUserEmail(): string | null {
    try {
      const storedUser = localStorage.getItem('user');
      if (!storedUser) {
        return null;
      }
      const parsed = JSON.parse(storedUser);
      return typeof parsed?.email === 'string' ? parsed.email : null;
    } catch {
      return null;
    }
  }
}
