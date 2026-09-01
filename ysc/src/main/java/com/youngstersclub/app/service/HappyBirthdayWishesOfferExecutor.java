package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.WhatsappTemplateExecutionRecipientDto;
import com.youngstersclub.app.dto.WhatsappTemplateExecutionResultDto;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.repository.ChildRepository;
import com.youngstersclub.app.repository.OrganizationRepository;
import com.youngstersclub.app.util.TimeUtil;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class HappyBirthdayWishesOfferExecutor implements WhatsAppTemplateExecutor {

    private static final Logger log = LoggerFactory.getLogger(HappyBirthdayWishesOfferExecutor.class);
    private static final String TEMPLATE_NAME = "happy_birthday_wishes_offer";

    private final ChildRepository childRepository;
    private final WhatsAppService whatsAppService;
    private final BrevoEmailService brevoEmailService;
    private final OrganizationRepository organizationRepository;
    private final OrganizationSummaryRecipientService organizationSummaryRecipientService;

    public HappyBirthdayWishesOfferExecutor(
            ChildRepository childRepository,
            WhatsAppService whatsAppService,
            BrevoEmailService brevoEmailService,
            OrganizationRepository organizationRepository,
            OrganizationSummaryRecipientService organizationSummaryRecipientService) {
        this.childRepository = childRepository;
        this.whatsAppService = whatsAppService;
        this.brevoEmailService = brevoEmailService;
        this.organizationRepository = organizationRepository;
        this.organizationSummaryRecipientService = organizationSummaryRecipientService;
    }

    @Override
    public String getTemplateName() {
        return TEMPLATE_NAME;
    }

    @Override
    public WhatsappTemplateExecutionResultDto execute(boolean isDryRun) {
        LocalDate today = TimeUtil.nowIST().toLocalDate();
        LocalDateTime executionTime = TimeUtil.nowIST();

        List<ChildRepository.BirthdayChildProjection> birthdayChildren = childRepository.findBirthdayChildrenByMonthAndDay(
                today.getMonthValue(),
                today.getDayOfMonth());
        String mode = isDryRun ? "DRY RUN" : "ACTUAL RUN";

        log.info(
                "Happy birthday wishes job started. Date: {}. Mode: {}. Today's birthdays: {}",
                today,
                mode,
                birthdayChildren.size());

        WhatsappTemplateExecutionResultDto result =
                buildCombinedResult(processOrganizations(groupByOrganization(birthdayChildren), isDryRun, executionTime));

        log.info(
                "Happy birthday wishes job completed. Mode: {}. Today's birthdays: {}, successful sends: {}, failed sends: {}",
                mode,
                result.getTotalCustomersScanned(),
                result.getSuccessfulMessages(),
                result.getFailedMessages());

        return result;
    }

    @Override
    public WhatsappTemplateExecutionResultDto executeForOrganization(Long organizationId, boolean isDryRun) {
        LocalDate today = TimeUtil.nowIST().toLocalDate();
        LocalDateTime executionTime = TimeUtil.nowIST();
        List<ChildRepository.BirthdayChildProjection> birthdayChildren = childRepository.findBirthdayChildrenByMonthAndDay(
                today.getMonthValue(),
                today.getDayOfMonth());

        return processSingleOrganization(
                organizationId,
                filterByOrganization(birthdayChildren, organizationId),
                isDryRun,
                executionTime);
    }

    protected Map<Long, List<ChildRepository.BirthdayChildProjection>> groupByOrganization(
            List<ChildRepository.BirthdayChildProjection> birthdayChildren) {
        Map<Long, List<ChildRepository.BirthdayChildProjection>> grouped = new LinkedHashMap<>();
        for (ChildRepository.BirthdayChildProjection child : birthdayChildren == null ? List.<ChildRepository.BirthdayChildProjection>of() : birthdayChildren) {
            if (child == null || child.getOrganizationId() == null) {
                continue;
            }
            grouped.computeIfAbsent(child.getOrganizationId(), ignored -> new ArrayList<>()).add(child);
        }
        return grouped;
    }

    protected List<WhatsappTemplateExecutionResultDto> processOrganizations(
            Map<Long, List<ChildRepository.BirthdayChildProjection>> childrenByOrganization,
            boolean isDryRun,
            LocalDateTime executionTime) {
        List<WhatsappTemplateExecutionResultDto> results = new ArrayList<>();
        for (Map.Entry<Long, List<ChildRepository.BirthdayChildProjection>> entry : childrenByOrganization.entrySet()) {
            results.add(processSingleOrganization(entry.getKey(), entry.getValue(), isDryRun, executionTime));
        }
        return results;
    }

    protected WhatsappTemplateExecutionResultDto processSingleOrganization(
            Long organizationId,
            List<ChildRepository.BirthdayChildProjection> birthdayChildren,
            boolean isDryRun,
            LocalDateTime executionTime) {
        List<ChildRepository.BirthdayChildProjection> safeBirthdayChildren =
                birthdayChildren == null ? List.of() : birthdayChildren;
        List<WhatsappTemplateExecutionRecipientDto> recipients = safeBirthdayChildren
                .stream()
                .map(this::toRecipient)
                .toList();

        int successCount = 0;
        int failedCount = 0;

        for (int index = 0; index < recipients.size(); index++) {
            WhatsappTemplateExecutionRecipientDto recipient = recipients.get(index);
            ChildRepository.BirthdayChildProjection child = safeBirthdayChildren.get(index);
            log.info(
                    "Happy birthday eligible parent. userId: {}, organization: {}, parentName: {}, kidName: {}",
                    recipient.getUserId(),
                    recipient.getOrganizationName(),
                    recipient.getName(),
                    recipient.getDetail());

            if (recipient.getPhone() == null || recipient.getPhone().isBlank()) {
                failedCount++;
                log.warn("Happy birthday wishes skipped for userId: {} because phone number is missing", recipient.getUserId());
                continue;
            }

            if (isDryRun) {
                successCount++;
                continue;
            }

            boolean sent = whatsAppService.sendHappyBirthdayWishesOfferMessage(
                    recipient.getPhone(),
                    recipient.getDetail(),
                    child.getOrganizationId(),
                    child.getBaseBranchId(),
                    child.getBaseBranchName(),
                    recipient.getUserId(),
                    recipient.getName());
            if (sent) {
                successCount++;
            } else {
                failedCount++;
                log.warn("Happy birthday wishes failed or skipped for userId: {}", recipient.getUserId());
            }
        }

        WhatsappTemplateExecutionResultDto result = new WhatsappTemplateExecutionResultDto(
                TEMPLATE_NAME,
                isDryRun,
                executionTime,
                recipients.size(),
                recipients.size(),
                0,
                successCount,
                failedCount,
                recipients);

        sendSummaryEmail(organizationId, result, resolveOrganizationEmail(organizationId));
        return result;
    }

    protected WhatsappTemplateExecutionRecipientDto toRecipient(ChildRepository.BirthdayChildProjection child) {
        return new WhatsappTemplateExecutionRecipientDto(
                child.getParentUserId(),
                child.getParentName(),
                child.getParentPhone(),
                BigDecimal.ZERO,
                child.getChildName(),
                child.getOrganizationName(),
                child.getBaseBranchName(),
                "BIRTHDAY TODAY");
    }

    protected List<ChildRepository.BirthdayChildProjection> filterByOrganization(
            List<ChildRepository.BirthdayChildProjection> birthdayChildren,
            Long organizationId) {
        if (organizationId == null || birthdayChildren == null || birthdayChildren.isEmpty()) {
            return List.of();
        }
        return birthdayChildren.stream()
                .filter(child -> organizationId.equals(child.getOrganizationId()))
                .toList();
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

    private void sendSummaryEmail(Long organizationId, WhatsappTemplateExecutionResultDto result, String organizationEmail) {
        try {
            List<String> adminEmails = organizationSummaryRecipientService.resolveRecipientsForOrganization(organizationId);
            int sentEmails = brevoEmailService.sendHappyBirthdayWishesSummaryEmail(result, adminEmails, organizationEmail);
            log.info(
                    "Happy birthday wishes summary email completed. organizationId: {}, mode: {}. Admin recipients emailed: {}",
                    organizationId,
                    result.isDryRun() ? "DRY RUN" : "ACTUAL RUN",
                    sentEmails);
        } catch (Exception ex) {
            log.error("Happy birthday wishes summary email failed. Reason: {}", ex.getMessage(), ex);
        }
    }

    protected String resolveOrganizationEmail(Long organizationId) {
        return organizationRepository.findByIdAndIsActiveTrue(organizationId)
                .map(Organization::getEmail)
                .orElse(null);
    }
}
