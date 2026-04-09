import { Component, inject, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';

interface TableStatus {
  tableName: string;
  isAvailable: boolean;
  players: string[];
}

@Component({
  selector: 'app-available-tables',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './available-tables.component.html',
  styleUrls: ['./available-tables.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AvailableTablesComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly cdr = inject(ChangeDetectorRef);

  tables: TableStatus[] = [];
  isLoading = true;
  isExpanded = false;

  ngOnInit() {
    this.fetchTables();
  }

  fetchTables() {
    this.isLoading = true;
    this.http.get<TableStatus[]>('/api/snooker/tables/status').subscribe({
      next: (res) => {
        this.tables = res;
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to fetch table statuses', err);
        this.isLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  toggleExpand() {
    this.isExpanded = !this.isExpanded;
  }
}
