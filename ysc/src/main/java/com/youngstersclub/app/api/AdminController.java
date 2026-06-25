package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.AdminMonthlyEarningsDto;
import com.youngstersclub.app.dto.ConsumableStockCreateRequest;
import com.youngstersclub.app.dto.ConsumableStockCreateResponseDto;
import com.youngstersclub.app.dto.ConsumableStockReportRowDto;
import com.youngstersclub.app.dto.MessageResponseDto;
import com.youngstersclub.app.dto.NotificationBroadcastRequest;
import com.youngstersclub.app.dto.TriggerWhatsappRequest;
import com.youngstersclub.app.service.ConsumableService;
import com.youngstersclub.app.service.AdminAnalyticsService;
import com.youngstersclub.app.service.AdminNotificationBroadcastService;
import com.youngstersclub.app.service.DailyCustomerEngagementService;
import com.youngstersclub.app.service.WhatsAppTemplateExecutionService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final AdminAnalyticsService adminAnalyticsService;
    private final ConsumableService consumableService;
    private final WhatsAppTemplateExecutionService whatsAppTemplateExecutionService;
    private final AdminNotificationBroadcastService adminNotificationBroadcastService;

    public AdminController(
            AdminAnalyticsService adminAnalyticsService,
            ConsumableService consumableService,
            WhatsAppTemplateExecutionService whatsAppTemplateExecutionService,
            AdminNotificationBroadcastService adminNotificationBroadcastService) {
        this.adminAnalyticsService = adminAnalyticsService;
        this.consumableService = consumableService;
        this.whatsAppTemplateExecutionService = whatsAppTemplateExecutionService;
        this.adminNotificationBroadcastService = adminNotificationBroadcastService;
    }

    @GetMapping("/monthly-earnings")
    public ResponseEntity<AdminMonthlyEarningsDto> getMonthlyEarnings(
            @RequestParam int month,
            @RequestParam int year) {
        return ResponseEntity.ok(adminAnalyticsService.getMonthlyEarnings(month, year));
    }

    @PostMapping("/consumables/stock")
    public ResponseEntity<ConsumableStockCreateResponseDto> addConsumableStock(
            @RequestBody ConsumableStockCreateRequest request) {
        return ResponseEntity.ok(consumableService.addStock(request));
    }

    @GetMapping("/consumables/stock-report")
    public ResponseEntity<List<ConsumableStockReportRowDto>> getConsumableStockReport(
            @RequestParam int month,
            @RequestParam int year) {
        return ResponseEntity.ok(consumableService.getStockReport(month, year));
    }

    @PostMapping("/trigger-whatsapp")
    public ResponseEntity<MessageResponseDto> triggerWhatsappMessages(@RequestBody(required = false) TriggerWhatsappRequest request) {
        boolean isDryRun = request != null && request.isDryRun();
        String templateName = request == null || request.getTemplateName() == null || request.getTemplateName().isBlank()
                ? "daily_visit_thanks_message"
                : request.getTemplateName();

        try {
            whatsAppTemplateExecutionService.triggerTemplateExecution(templateName, isDryRun);
        } catch (Exception ex) {
            log.error(
                    "Failed to queue manual WhatsApp trigger. templateName: {}, mode: {}. Reason: {}",
                    templateName,
                    isDryRun ? "DRY RUN" : "ACTUAL RUN",
                    ex.getMessage(),
                    ex);
        }

        return ResponseEntity.ok(new MessageResponseDto("Process triggered successfully"));
    }

    @PostMapping("/send-notification-message")
    public ResponseEntity<MessageResponseDto> sendNotificationMessage(
            @RequestBody(required = false) NotificationBroadcastRequest request) {
        try {
            adminNotificationBroadcastService.triggerNotificationBroadcast(
                    request == null ? null : request.getMessage(),
                    request == null ? null : request.getRecipientType(),
                    request == null ? null : request.getCustomerIds());
        } catch (Exception ex) {
            log.error("Failed to queue notification broadcast. Reason: {}", ex.getMessage(), ex);
        }

        return ResponseEntity.ok(new MessageResponseDto("Notification process triggered successfully"));
    }
}
