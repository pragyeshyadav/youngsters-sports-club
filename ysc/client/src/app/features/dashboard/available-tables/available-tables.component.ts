import { Component, inject, OnDestroy, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { OrganizationContextService } from '../../../core/services/organization-context.service';

interface TableStatus {
  id: number;
  tableName: string;
  available: boolean;
  players: string[];
  branchId?: number | null;
  branchName?: string | null;
}

@Component({
  selector: 'app-available-tables',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './available-tables.component.html',
  styleUrls: ['./available-tables.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AvailableTablesComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly authService = inject(AuthService);
  private readonly organizationContextService = inject(OrganizationContextService);
  private readonly subscriptions = new Subscription();

  tables: TableStatus[] = [];
  isLoading = true;
  isExpanded = false;
  currentBranchId: number | null = null;
  private latestRequestId = 0;

  ngOnInit() {
    this.subscribeToBranchChanges();
  }

  ngOnDestroy() {
    this.subscriptions.unsubscribe();
  }

  fetchTables() {
    const actorEmail = this.authService.getSnapshot()?.user.email;
    if (!actorEmail) {
      this.tables = [];
      this.isLoading = false;
      this.cdr.markForCheck();
      return;
    }

    this.isLoading = true;
    const requestId = ++this.latestRequestId;
    const headers = new HttpHeaders({
      'X-User-Email': actorEmail,
    });
    this.http.get<TableStatus[]>('/api/snooker/tables/status', { headers }).subscribe({
      next: (res) => {
        if (requestId !== this.latestRequestId) {
          return;
        }
        this.tables = res.filter((table) => table.tableName !== 'Kids Ocean Dream Land');
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        if (requestId !== this.latestRequestId) {
          return;
        }
        console.error('Failed to fetch table statuses', err);
        this.isLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  toggleExpand() {
    this.isExpanded = !this.isExpanded;
  }

  private subscribeToBranchChanges(): void {
    const snapshot = this.organizationContextService.getSnapshot();
    if (snapshot?.currentBranch?.id) {
      this.currentBranchId = snapshot.currentBranch.id;
      this.fetchTables();
    } else {
      this.isLoading = false;
    }

    this.subscriptions.add(
      this.organizationContextService.context$.subscribe((context) => {
        const nextBranchId = context?.currentBranch?.id ?? null;
        if (this.currentBranchId === nextBranchId) {
          return;
        }

        this.currentBranchId = nextBranchId;
        this.latestRequestId++;
        this.tables = [];
        this.isLoading = true;
        this.cdr.markForCheck();

        if (nextBranchId) {
          this.fetchTables();
          return;
        }

        this.isLoading = false;
        this.cdr.markForCheck();
      }),
    );
  }
}
