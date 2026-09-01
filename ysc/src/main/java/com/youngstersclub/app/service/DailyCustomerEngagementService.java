package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.DailyVisitedOrganizationDto;
import com.youngstersclub.app.dto.WhatsappTemplateExecutionRecipientDto;
import com.youngstersclub.app.dto.WhatsappTemplateExecutionResultDto;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.repository.DailyCustomerVisitRepository;
import com.youngstersclub.app.repository.OrganizationRepository;
import com.youngstersclub.app.util.TimeUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final WhatsAppService whatsAppService;
    private final BrevoEmailService brevoEmailService;
    private final OrganizationRepository organizationRepository;
    private final OrganizationSummaryRecipientService organizationSummaryRecipientService;

    public DailyCustomerEngagementService(
            DailyCustomerVisitRepository dailyCustomerVisitRepository,
            WhatsAppService whatsAppService,
            BrevoEmailService brevoEmailService,
            OrganizationRepository organizationRepository,
            OrganizationSummaryRecipientService organizationSummaryRecipientService) {
        this.dailyCustomerVisitRepository = dailyCustomerVisitRepository;
        this.whatsAppService = whatsAppService;
        this.brevoEmailService = brevoEmailService;
        this.organizationRepository = organizationRepository;
        this.organizationSummaryRecipientService = organizationSummaryRecipientService;
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
            log.error(
                    "Manual daily WhatsApp trigger failed. Mode: {}. Reason: {}",
                    isDryRun ? "DRY RUN" : "ACTUAL RUN",
                    ex.getMessage(),
                    ex);
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

    @Override
    public WhatsappTemplateExecutionResultDto executeForOrganization(Long organizationId, boolean isDryRun) {
        return processDailyWhatsappNotificationsForOrganization(organizationId, isDryRun);
    }

    public WhatsappTemplateExecutionResultDto processDailyWhatsappNotifications(boolean isDryRun) {
        LocalDate today = TimeUtil.nowIST().toLocalDate();
        LocalDateTime executionTime = TimeUtil.nowIST();
        List<DailyVisitedOrganizationDto> visitedCustomers =
                dailyCustomerVisitRepository.findDailyVisitedCustomersByOrganization(today);
        String mode = isDryRun ? "DRY RUN" : "ACTUAL RUN";

        log.info(
                "Daily visit thank-you job started for date: {}. Mode: {}. Total users identified: {}",
                today,
                mode,
                visitedCustomers.size());

        WhatsappTemplateExecutionResultDto result = buildCombinedResult(
                processOrganizations(visitedCustomersByOrganization(visitedCustomers), isDryRun, executionTime));

        log.info(
                "Daily visit thank-you job completed for date: {}. Mode: {}. Total users processed: {}, messages sent successfully: {}, failures: {}",
                today,
                mode,
                result.getTotalCustomersScanned(),
                result.getSuccessfulMessages(),
                result.getFailedMessages());

        return result;
    }

    protected WhatsappTemplateExecutionResultDto processDailyWhatsappNotificationsForOrganization(
            Long organizationId,
            boolean isDryRun) {
        LocalDate today = TimeUtil.nowIST().toLocalDate();
        LocalDateTime executionTime = TimeUtil.nowIST();
        List<DailyVisitedOrganizationDto> visitedCustomers =
                dailyCustomerVisitRepository.findDailyVisitedCustomersByOrganization(today);

        return processSingleOrganization(
                organizationId,
                filterByOrganization(visitedCustomers, organizationId),
                isDryRun,
                executionTime);
    }

    protected Map<Long, List<DailyVisitedOrganizationDto>> visitedCustomersByOrganization(
            List<DailyVisitedOrganizationDto> visitedCustomers) {
        Map<Long, List<DailyVisitedOrganizationDto>> grouped = new LinkedHashMap<>();
        for (DailyVisitedOrganizationDto customer : visitedCustomers == null ? List.<DailyVisitedOrganizationDto>of() : visitedCustomers) {
            if (customer == null || customer.getOrganizationId() == null) {
                continue;
            }
            grouped.computeIfAbsent(customer.getOrganizationId(), ignored -> new ArrayList<>()).add(customer);
        }
        return grouped;
    }

    protected List<WhatsappTemplateExecutionResultDto> processOrganizations(
            Map<Long, List<DailyVisitedOrganizationDto>> customersByOrganization,
            boolean isDryRun,
            LocalDateTime executionTime) {
        List<WhatsappTemplateExecutionResultDto> results = new ArrayList<>();
        for (Map.Entry<Long, List<DailyVisitedOrganizationDto>> entry : customersByOrganization.entrySet()) {
            results.add(processSingleOrganization(entry.getKey(), entry.getValue(), isDryRun, executionTime));
        }
        return results;
    }

    protected WhatsappTemplateExecutionResultDto processSingleOrganization(
            Long organizationId,
            List<DailyVisitedOrganizationDto> customers,
            boolean isDryRun,
            LocalDateTime executionTime) {
        int totalUsers = customers == null ? 0 : customers.size();
        int sentCount = 0;
        int failedCount = 0;
        List<WhatsappTemplateExecutionRecipientDto> recipientSummaries = new ArrayList<>();

        for (DailyVisitedOrganizationDto customer : customers == null ? List.<DailyVisitedOrganizationDto>of() : customers) {
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

            boolean sent = whatsAppService.sendDailyVisitThankYouMessage(
                    customer.getPhone(),
                    customer.getName(),
                    customer.getOrganizationId(),
                    customer.getOrganizationName(),
                    customer.getBranchId(),
                    customer.getBranchName(),
                    customer.getUserId());
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

        sendDailySummaryEmail(organizationId, result, resolveOrganizationEmail(organizationId));
        return result;
    }

    protected WhatsappTemplateExecutionResultDto buildCombinedResult(List<WhatsappTemplateExecutionResultDto> results) {
        List<WhatsappTemplateExecutionRecipientDto> recipients = new ArrayList<>();
        int totalCustomersScanned = 0;
        int eligibleCustomers = 0;
        int skippedCustomers = 0;
        int successfulMessages = 0;
        int failedMessages = 0;
        boolean dryRun = false;
        LocalDateTime executionTime = TimeUtil.nowIST();

        for (WhatsappTemplateExecutionResultDto result : results == null ? List.<WhatsappTemplateExecutionResultDto>of() : results) {
            if (result == null) {
                continue;
            }
            dryRun = result.isDryRun();
            executionTime = result.getExecutionTime();
            totalCustomersScanned += result.getTotalCustomersScanned();
            eligibleCustomers += result.getEligibleCustomers();
            skippedCustomers += result.getSkippedCustomers();
            successfulMessages += result.getSuccessfulMessages();
            failedMessages += result.getFailedMessages();
            if (result.getRecipients() != null) {
                recipients.addAll(result.getRecipients());
            }
        }

        return new WhatsappTemplateExecutionResultDto(
                TEMPLATE_NAME,
                dryRun,
                executionTime,
                totalCustomersScanned,
                eligibleCustomers,
                skippedCustomers,
                successfulMessages,
                failedMessages,
                recipients);
    }

    protected List<DailyVisitedOrganizationDto> filterByOrganization(
            List<DailyVisitedOrganizationDto> customers,
            Long organizationId) {
        if (organizationId == null || customers == null || customers.isEmpty()) {
            return List.of();
        }
        return customers.stream()
                .filter(customer -> organizationId.equals(customer.getOrganizationId()))
                .toList();
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

    private void sendDailySummaryEmail(Long organizationId, WhatsappTemplateExecutionResultDto result, String organizationEmail) {
        try {
            List<String> adminEmails = organizationSummaryRecipientService.resolveRecipientsForOrganization(organizationId);
            int emailSentCount = brevoEmailService.sendDailyVisitSummaryEmail(result, adminEmails, organizationEmail);
            log.info(
                    "Daily WhatsApp summary email completed. organizationId: {}, mode: {}. Successful customer messages: {}, admin recipients emailed: {}",
                    organizationId,
                    result.isDryRun() ? "DRY RUN" : "ACTUAL RUN",
                    result.getSuccessfulMessages(),
                    emailSentCount);
        } catch (Exception ex) {
            log.error("Daily WhatsApp summary email failed. Reason: {}", ex.getMessage(), ex);
        }
    }

    protected String resolveOrganizationEmail(Long organizationId) {
        return organizationRepository.findByIdAndIsActiveTrue(organizationId)
                .map(Organization::getEmail)
                .orElse(null);
    }
}
