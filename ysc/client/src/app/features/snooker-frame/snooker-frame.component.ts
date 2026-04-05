import { Component, inject, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BrandTitleComponent } from '../../shared/components/brand-title/brand-title.component';
import { ClubLogoComponent } from '../../shared/components/club-logo/club-logo.component';

@Component({
  selector: 'app-snooker-frame',
  standalone: true,
  imports: [BrandTitleComponent, ClubLogoComponent],
  templateUrl: './snooker-frame.component.html',
  styleUrl: './snooker-frame.component.scss'
})
export class SnookerFrameComponent implements OnInit {
  private http = inject(HttpClient);

  tables: any[] = [];
  selectedTable: any = null;

  ngOnInit() {
    this.http.get('/api/snooker/tables').subscribe({
      next: (res: any) => {
        this.tables = res;
      },
      error: (err) => {
        console.error('Failed to fetch tables', err);
      }
    });
  }

  selectTable(table: any) {
    this.selectedTable = table;
  }
}
