package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.DailyVisitedOrganizationDto;
import com.youngstersclub.app.dto.WhatsappTemplateExecutionRecipientDto;
import com.youngstersclub.app.dto.WhatsappTemplateExecutionResultDto;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.DailyCustomerVisitRepository;
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

    private final DailyCustomerVisitRepository dailyCustomerVisitRepository;
    private final UserRepository userRepository;
    private final WhatsAppService whatsAppService;
    private final BrevoEmailService brevoEmailService;

    public DailyCustomerEngagementService(
            DailyCustomerVisitRepository dailyCustomerVisitRepository,
            UserRepository userRepository,
            WhatsAppService whatsAppService,
            BrevoEmailService brevoEmailService) {
        this.dailyCustomerVisitRepository = dailyCustomerVisitRepository;
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
        List<DailyVisitedOrganizationDto> visitedCustomers =
                dailyCustomerVisitRepository.findDailyVisitedCustomersByOrganization(today);
        int totalUsers = visitedCustomers.size();
        int sentCount = 0;
        int failedCount = 0;
        List<WhatsappTemplateExecutionRecipientDto> recipientSummaries = new ArrayList<>();
        String mode = isDryRun ? "DRY RUN" : "ACTUAL RUN";

        log.info("Daily visit thank-you job started for date: {}. Mode: {}. Total users identified: {}", today, mode, totalUsers);

        for (DailyVisitedOrganizationDto customer : visitedCustomers) {
            if (customer.getPhone() == null || customer.getPhone().isBlank()) {
                log.warn(
                        "Daily visit thank-you skipped for userId: {}, organizationId: {} because phone number is missing",
                        customer.getUserId(),
                        customer.getOrganizationId());
                failedCount++;
                continue;
            }

            if (isDryRun) {
                sentCount++;
                recipientSummaries.add(buildRecipientSummary(customer));
                continue;
            }

            boolean sent = whatsAppService.sendDailyVisitThankYouMessage(customer.getPhone(), customer.getName());
            if (sent) {
                sentCount++;
                recipientSummaries.add(buildRecipientSummary(customer));
            } else {
                failedCount++;
                log.warn(
                        "Daily visit thank-you message failed or skipped for userId: {}, organizationId: {}",
                        customer.getUserId(),
                        customer.getOrganizationId());
            }
        }

        WhatsappTemplateExecutionResultDto result = new WhatsappTemplateExecutionResultDto(
                TEMPLATE_NAME,
                isDryRun,
                executionTime,
                totalUsers,
                recipientSummaries.size(),
                Math.max(totalUsers - recipientSummaries.size(), 0),
                sentCount,
                failedCount,
                recipientSummaries);

        sendDailySummaryEmail(result);

        log.info(
                "Daily visit thank-you job completed for date: {}. Mode: {}. Total users processed: {}, messages sent successfully: {}, failures: {}",
                today,
                mode,
                totalUsers,
                sentCount,
                failedCount);

        return result;
    }

    protected WhatsappTemplateExecutionRecipientDto buildRecipientSummary(DailyVisitedOrganizationDto customer) {
        return new WhatsappTemplateExecutionRecipientDto(
                customer.getUserId(),
                customer.getName(),
                customer.getPhone(),
                null,
                null,
                customer.getOrganizationName(),
                customer.getBranchName(),
                "VISITED TODAY");
    }

    private void sendDailySummaryEmail(WhatsappTemplateExecutionResultDto result) {
        try {
            List<String> adminEmails = userRepository.findByRoleInAndIsActiveTrue(List.of(UserRole.ADMIN, UserRole.SUPER_ADMIN))
                    .stream()
                    .map(User::getEmail)
                    .filter(email -> email != null && !email.isBlank())
                    .collect(Collectors.toList());

            int emailSentCount = brevoEmailService.sendDailyVisitSummaryEmail(result, adminEmails);
            log.info(
                    "Daily WhatsApp summary email completed. Mode: {}. Successful customer messages: {}, admin recipients emailed: {}",
                    result.isDryRun() ? "DRY RUN" : "ACTUAL RUN",
                    result.getSuccessfulMessages(),
                    emailSentCount);
        } catch (Exception ex) {
            log.error("Daily WhatsApp summary email failed. Reason: {}", ex.getMessage(), ex);
        }
    }
}
