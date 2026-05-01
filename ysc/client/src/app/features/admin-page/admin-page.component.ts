import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { BrandTitleComponent } from '../../shared/components/brand-title/brand-title.component';
import { ClubLogoComponent } from '../../shared/components/club-logo/club-logo.component';
import { AuthService } from '../../core/services/auth.service';

interface AdminMonthlyEarnings {
  currentMonthTotal: number | string | null;
  previousMonthTotal: number | string | null;
  snookerEarnings: number | string | null;
  snookerTableBreakdown: Record<string, number | string | null>;
  consumableEarnings: number | string | null;
  kidsZoneEarnings: number | string | null;
}

interface SnookerBreakdownEntry {
  tableName: string;
  amount: number | string | null;
}

@Component({
  selector: 'app-admin-page',
  standalone: true,
  imports: [CommonModule, FormsModule, BrandTitleComponent, ClubLogoComponent],
  templateUrl: './admin-page.component.html',
  styleUrl: './admin-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminPageComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);

  canViewAdminReport = false;
  isMonthlyReportExpanded = false;
  isLoadingMonthlyReport = false;
  reportError = '';
  selectedMonth = '';
  selectedYear = '';
  monthOptions = [
    { value: '01', label: 'January' },
    { value: '02', label: 'February' },
    { value: '03', label: 'March' },
    { value: '04', label: 'April' },
    { value: '05', label: 'May' },
    { value: '06', label: 'June' },
    { value: '07', label: 'July' },
    { value: '08', label: 'August' },
    { value: '09', label: 'September' },
    { value: '10', label: 'October' },
    { value: '11', label: 'November' },
    { value: '12', label: 'December' },
  ];
  yearOptions: string[] = [];
  snookerBreakdownEntries: SnookerBreakdownEntry[] = [];
  monthlyEarnings: AdminMonthlyEarnings = {
    currentMonthTotal: 0,
    previousMonthTotal: 0,
    snookerEarnings: 0,
    snookerTableBreakdown: {},
    consumableEarnings: 0,
    kidsZoneEarnings: 0,
  };

  ngOnInit(): void {
    const today = new Date();
    this.selectedMonth = `${today.getMonth() + 1}`.padStart(2, '0');
    this.selectedYear = `${today.getFullYear()}`;
    this.yearOptions = [this.selectedYear, `${today.getFullYear() - 1}`];

    const email = this.auth.getSnapshot()?.user.email;
    if (!email) {
      this.cdr.markForCheck();
      return;
    }

    this.http.get<{ role?: string }>(`/api/user?email=${encodeURIComponent(email)}`).subscribe({
      next: (user) => {
        this.canViewAdminReport = ['ADMIN', 'SUPER_ADMIN'].includes(user?.role ?? '');
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load admin access', err);
        this.canViewAdminReport = false;
        this.cdr.markForCheck();
      },
    });
  }

  goBack(): void {
    void this.router.navigate(['/dashboard']);
  }

  toggleMonthlyReport(): void {
    if (!this.canViewAdminReport) {
      return;
    }

    this.isMonthlyReportExpanded = !this.isMonthlyReportExpanded;
    if (this.isMonthlyReportExpanded) {
      this.loadMonthlyReport();
    }
  }

  onFilterChange(): void {
    if (!this.isMonthlyReportExpanded) {
      return;
    }
    this.loadMonthlyReport();
  }

  private loadMonthlyReport(): void {
    this.isLoadingMonthlyReport = true;
    this.reportError = '';
    this.cdr.markForCheck();

    this.http.get<AdminMonthlyEarnings>(`/api/admin/monthly-earnings?month=${this.selectedMonth}&year=${this.selectedYear}`).subscribe({
      next: (report) => {
        this.monthlyEarnings = {
          currentMonthTotal: report?.currentMonthTotal ?? 0,
          previousMonthTotal: report?.previousMonthTotal ?? 0,
          snookerEarnings: report?.snookerEarnings ?? 0,
          snookerTableBreakdown: report?.snookerTableBreakdown ?? {},
          consumableEarnings: report?.consumableEarnings ?? 0,
          kidsZoneEarnings: report?.kidsZoneEarnings ?? 0,
        };
        this.snookerBreakdownEntries = Object.entries(report?.snookerTableBreakdown ?? {}).map(([tableName, amount]) => ({
          tableName,
          amount,
        }));
        this.isLoadingMonthlyReport = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load monthly earnings report', err);
        this.monthlyEarnings = {
          currentMonthTotal: 0,
          previousMonthTotal: 0,
          snookerEarnings: 0,
          snookerTableBreakdown: {},
          consumableEarnings: 0,
          kidsZoneEarnings: 0,
        };
        this.snookerBreakdownEntries = [];
        this.reportError = err?.error?.message || 'Unable to load monthly report right now';
        this.isLoadingMonthlyReport = false;
        this.cdr.markForCheck();
      },
    });
  }
}
