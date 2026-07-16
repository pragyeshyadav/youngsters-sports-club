package com.youngstersclub.app.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PaymentDueReminderSchedulerService {

    private final WhatsAppTemplateExecutionService whatsAppTemplateExecutionService;

    public PaymentDueReminderSchedulerService(WhatsAppTemplateExecutionService whatsAppTemplateExecutionService) {
        this.whatsAppTemplateExecutionService = whatsAppTemplateExecutionService;
    }

    @Scheduled(cron = "0 30 11 * * *", zone = "Asia/Kolkata")
    public void sendPaymentDueReminderMessages() {
        whatsAppTemplateExecutionService.executeTemplate("payment_due_reminder", false);
    }
}
