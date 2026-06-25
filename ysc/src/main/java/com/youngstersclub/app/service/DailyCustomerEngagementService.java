package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.WhatsappTemplateExecutionRecipientDto;
import com.youngstersclub.app.dto.WhatsappTemplateExecutionResultDto;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.UserRepository;
import com.youngstersclub.app.util.TimeUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DailyCustomerEngagementService implements WhatsAppTemplateExecutor {

    private static final Logger log = LoggerFactory.getLogger(DailyCustomerEngagementService.class);
    private static final String TEMPLATE_NAME = "daily_visit_thanks_message";

    private final UserRepository userRepository;
    private final WhatsAppService whatsAppService;
    private final BrevoEmailService brevoEmailService;

    public DailyCustomerEngagementService(
            UserRepository userRepository,
            WhatsAppService whatsAppService,
            BrevoEmailService brevoEmailService) {
        this.userRepository = userRepository;
        this.whatsAppService = whatsAppService;
        this.brevoEmailService = brevoEmailService;
    }

    @Scheduled(cron = "0 30 21 * * *", zone = "Asia/Kolkata")
    public void sendDailyVisitThankYouMessages() {
        processDailyWhatsappNotifications(false);
    }

    @Async
    public void triggerDailyWhatsappNotifications(boolean isDryRun) {
        try {
            processDailyWhatsappNotifications(isDryRun);
        } catch (Exception ex) {
            log.error("Manual daily WhatsApp trigger failed. Mode: {}. Reason: {}", isDryRun ? "DRY RUN" : "ACTUAL RUN", ex.getMessage(), ex);
        }
    }

    @Override
    public String getTemplateName() {
        return TEMPLATE_NAME;
    }

    @Override
    public WhatsappTemplateExecutionResultDto execute(boolean isDryRun) {
        return processDailyWhatsappNotifications(isDryRun);
    }

    public WhatsappTemplateExecutionResultDto processDailyWhatsappNotifications(boolean isDryRun) {
        LocalDate today = TimeUtil.nowIST().toLocalDate();
        LocalDateTime executionTime = TimeUtil.nowIST();
        List<UserRepository.DailyVisitedCustomerProjection> visitedCustomers = userRepository.findDailyVisitedCustomers(today);

        int totalUsers = visitedCustomers.size();
        int sentCount = 0;
        int failedCount = 0;
        List<UserRepository.DailyVisitedCustomerProjection> processedCustomers = new ArrayList<>();
        List<WhatsappTemplateExecutionRecipientDto> recipientSummaries = new ArrayList<>();
        String mode = isDryRun ? "DRY RUN" : "ACTUAL RUN";

        log.info("Daily visit thank-you job started for date: {}. Mode: {}. Total users identified: {}", today, mode, totalUsers);

        for (UserRepository.DailyVisitedCustomerProjection customer : visitedCustomers) {
            if (customer.getPhone() == null || customer.getPhone().isBlank()) {
                log.warn("Daily visit thank-you skipped for userId: {} because phone number is missing", customer.getUserId());
                failedCount++;
                continue;
            }

            if (isDryRun) {
                processedCustomers.add(customer);
                sentCount++;
                recipientSummaries.add(new WhatsappTemplateExecutionRecipientDto(
                        customer.getUserId(),
                        customer.getName(),
                        customer.getPhone(),
                        null));
                continue;
            }

            boolean sent = whatsAppService.sendDailyVisitThankYouMessage(customer.getPhone(), customer.getName());
            if (sent) {
                sentCount++;
                processedCustomers.add(customer);
                recipientSummaries.add(new WhatsappTemplateExecutionRecipientDto(
                        customer.getUserId(),
                        customer.getName(),
                        customer.getPhone(),
                        null));
            } else {
                failedCount++;
                log.warn("Daily visit thank-you message failed or skipped for userId: {}", customer.getUserId());
            }
        }

        sendDailySummaryEmail(processedCustomers, isDryRun);

        log.info(
                "Daily visit thank-you job completed for date: {}. Mode: {}. Total users processed: {}, messages sent successfully: {}, failures: {}",
                today,
                mode,
                totalUsers,
                sentCount,
                failedCount);

        return new WhatsappTemplateExecutionResultDto(
                TEMPLATE_NAME,
                isDryRun,
                executionTime,
                totalUsers,
                processedCustomers.size(),
                Math.max(totalUsers - processedCustomers.size(), 0),
                sentCount,
                failedCount,
                recipientSummaries);
    }

    private void sendDailySummaryEmail(List<UserRepository.DailyVisitedCustomerProjection> processedCustomers, boolean isDryRun) {
        try {
            List<String> adminEmails = userRepository.findByRoleInAndIsActiveTrue(List.of(UserRole.ADMIN, UserRole.SUPER_ADMIN))
                    .stream()
                    .map(User::getEmail)
                    .filter(email -> email != null && !email.isBlank())
                    .collect(Collectors.toList());

            int emailSentCount = brevoEmailService.sendSummaryEmail(processedCustomers, adminEmails, isDryRun);
            log.info(
                    "Daily WhatsApp summary email completed. Mode: {}. Successful customer messages: {}, admin recipients emailed: {}",
                    isDryRun ? "DRY RUN" : "ACTUAL RUN",
                    processedCustomers.size(),
                    emailSentCount);
        } catch (Exception ex) {
            log.error("Daily WhatsApp summary email failed. Reason: {}", ex.getMessage(), ex);
        }
    }
}
