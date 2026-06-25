import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

interface MessageResponse {
  message: string;
}

interface CustomerOption {
  id: number;
  name: string;
  email: string;
}

type RecipientType = 'SNOOKER_PLAYERS' | 'ALL_CUSTOMERS' | 'SELECTED_CUSTOMERS';
type SchedulerTemplate = 'daily_visit_thanks_message' | 'payment_due_reminder';

@Component({
  selector: 'app-trigger-whatsapp-panel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './trigger-whatsapp-panel.component.html',
  styleUrl: './trigger-whatsapp-panel.component.scss',
})
export class TriggerWhatsappPanelComponent {
  private readonly http = inject(HttpClient);
  private readonly cdr = inject(ChangeDetectorRef);
  private customerSearchRequestId = 0;

  isExpanded = false;
  isTriggeringWhatsapp = false;
  activeTriggerMode: 'DRY_RUN' | 'ACTUAL' | null = null;
  selectedSchedulerTemplate: SchedulerTemplate = 'daily_visit_thanks_message';
  notificationMessage = '';
  recipientType: RecipientType = 'SNOOKER_PLAYERS';
  customerSearchText = '';
  customerOptions: CustomerOption[] = [];
  selectedCustomers: CustomerOption[] = [];
  isSearchingCustomers = false;
  isSendingNotification = false;

  toggle(): void {
    this.isExpanded = !this.isExpanded;
    this.cdr.markForCheck();
  }

  triggerWhatsappMessages(dryRun: boolean): void {
    if (this.isTriggeringWhatsapp) {
      return;
    }

    this.isTriggeringWhatsapp = true;
    this.activeTriggerMode = dryRun ? 'DRY_RUN' : 'ACTUAL';

    this.http.post<MessageResponse>('/api/admin/trigger-whatsapp', {
      dryRun,
      templateName: this.selectedSchedulerTemplate,
    }).subscribe({
      next: (response) => {
        this.isTriggeringWhatsapp = false;
        this.activeTriggerMode = null;
        this.cdr.detectChanges();
        alert(response?.message || 'Process triggered successfully');
      },
      error: (err) => {
        console.error('Failed to trigger WhatsApp process', err);
        this.isTriggeringWhatsapp = false;
        this.activeTriggerMode = null;
        this.cdr.detectChanges();
        alert(err?.error?.message || 'Process triggered successfully');
      },
    });
  }

  searchCustomers(): void {
    const query = this.customerSearchText.trim();
    const requestId = ++this.customerSearchRequestId;

    if (query.length < 3 || this.recipientType !== 'SELECTED_CUSTOMERS') {
      this.customerOptions = [];
      this.isSearchingCustomers = false;
      this.cdr.markForCheck();
      return;
    }

    this.isSearchingCustomers = true;
    this.cdr.markForCheck();
    this.http.get<unknown>(`/api/users/search?query=${encodeURIComponent(query)}`).pipe(
      finalize(() => {
        if (requestId === this.customerSearchRequestId) {
          this.isSearchingCustomers = false;
          this.cdr.markForCheck();
        }
      }),
    ).subscribe({
      next: (customers) => {
        if (requestId !== this.customerSearchRequestId) {
          return;
        }

        const mappedCustomers = this.normalizeCustomerOptions(customers);
        const selectedIds = new Set(this.selectedCustomers.map((customer) => customer.id));
        this.customerOptions = mappedCustomers
          .filter((customer) => !selectedIds.has(customer.id))
          .slice(0, 10);
        this.cdr.markForCheck();
      },
      error: (err) => {
        if (requestId !== this.customerSearchRequestId) {
          return;
        }
        console.error('Failed to search customers', err);
        this.customerOptions = [];
        this.cdr.markForCheck();
      },
    });
  }

  setRecipientType(type: RecipientType): void {
    this.recipientType = type;
    this.customerSearchText = '';
    this.customerOptions = [];
    this.isSearchingCustomers = false;
    this.customerSearchRequestId++;
    if (type !== 'SELECTED_CUSTOMERS') {
      this.selectedCustomers = [];
    }
    this.cdr.markForCheck();
  }

  selectCustomer(customer: CustomerOption): void {
    if (this.selectedCustomers.length >= 20) {
      alert('You can select up to 20 customers');
      return;
    }

    if (this.selectedCustomers.some((selected) => selected.id === customer.id)) {
      return;
    }

    this.selectedCustomers = [...this.selectedCustomers, customer];
    this.customerSearchText = '';
    this.customerOptions = [];
    this.isSearchingCustomers = false;
    this.customerSearchRequestId++;
    this.cdr.markForCheck();
  }

  removeCustomer(customerId: number): void {
    this.selectedCustomers = this.selectedCustomers.filter((customer) => customer.id !== customerId);
    this.cdr.markForCheck();
  }

  canSendNotification(): boolean {
    if (this.isSendingNotification || !this.notificationMessage.trim()) {
      return false;
    }
    if (this.recipientType !== 'SELECTED_CUSTOMERS') {
      return true;
    }
    return this.selectedCustomers.length > 0;
  }

  sendNotificationMessage(): void {
    if (!this.canSendNotification()) {
      return;
    }

    this.isSendingNotification = true;
    this.cdr.markForCheck();
    this.http.post<MessageResponse>('/api/admin/send-notification-message', {
      message: this.notificationMessage.trim(),
      recipientType: this.recipientType,
      customerIds: this.recipientType === 'SELECTED_CUSTOMERS'
        ? this.selectedCustomers.map((customer) => customer.id)
        : undefined,
    }).pipe(
      finalize(() => {
        this.isSendingNotification = false;
        this.cdr.markForCheck();
      }),
    ).subscribe({
      next: (response) => {
        this.resetNotificationComposer();
        this.cdr.detectChanges();
        alert(response?.message || 'Process triggered successfully');
      },
      error: (err) => {
        console.error('Failed to trigger notification broadcast', err);
        this.cdr.detectChanges();
        alert(err?.error?.message || 'Process triggered successfully');
      },
    });
  }

  private resetNotificationComposer(): void {
    this.notificationMessage = '';
    this.customerSearchText = '';
    this.customerOptions = [];
    this.selectedCustomers = [];
    this.isSearchingCustomers = false;
    this.customerSearchRequestId++;
  }

  private normalizeCustomerOptions(response: unknown): CustomerOption[] {
    if (!Array.isArray(response)) {
      return [];
    }

    return response
      .map((customer: any) => ({
        id: Number(customer?.id),
        name: typeof customer?.name === 'string' ? customer.name.trim() : '',
        email: typeof customer?.email === 'string' ? customer.email.trim() : '',
      }))
      .filter((customer) => Number.isFinite(customer.id) && customer.id > 0 && customer.name.length > 0);
  }
}
