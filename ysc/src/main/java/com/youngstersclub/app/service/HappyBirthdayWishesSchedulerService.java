package com.youngstersclub.app.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class HappyBirthdayWishesSchedulerService {

    private final WhatsAppTemplateExecutionService whatsAppTemplateExecutionService;

    public HappyBirthdayWishesSchedulerService(WhatsAppTemplateExecutionService whatsAppTemplateExecutionService) {
        this.whatsAppTemplateExecutionService = whatsAppTemplateExecutionService;
    }

    @Scheduled(cron = "0 45 11 * * *", zone = "Asia/Kolkata")
    public void sendHappyBirthdayWishesMessages() {
        whatsAppTemplateExecutionService.executeTemplate("happy_birthday_wishes_offer", false);
    }
}
