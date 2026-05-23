import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, inject } from '@angular/core';

interface MessageResponse {
  message: string;
}

@Component({
  selector: 'app-trigger-whatsapp-panel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './trigger-whatsapp-panel.component.html',
  styleUrl: './trigger-whatsapp-panel.component.scss',
})
export class TriggerWhatsappPanelComponent {
  private readonly http = inject(HttpClient);

  isExpanded = false;
  isTriggeringWhatsapp = false;
  activeTriggerMode: 'DRY_RUN' | 'ACTUAL' | null = null;

  toggle(): void {
    this.isExpanded = !this.isExpanded;
  }

  triggerWhatsappMessages(dryRun: boolean): void {
    if (this.isTriggeringWhatsapp) {
      return;
    }

    this.isTriggeringWhatsapp = true;
    this.activeTriggerMode = dryRun ? 'DRY_RUN' : 'ACTUAL';

    this.http.post<MessageResponse>('/api/admin/trigger-whatsapp', { dryRun }).subscribe({
      next: (response) => {
        this.isTriggeringWhatsapp = false;
        this.activeTriggerMode = null;
        alert(response?.message || 'Process triggered successfully');
      },
      error: (err) => {
        console.error('Failed to trigger WhatsApp process', err);
        this.isTriggeringWhatsapp = false;
        this.activeTriggerMode = null;
        alert(err?.error?.message || 'Process triggered successfully');
      },
    });
  }
}
