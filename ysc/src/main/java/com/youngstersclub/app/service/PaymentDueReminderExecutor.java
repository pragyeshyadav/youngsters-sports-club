package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.UserPaymentSummaryDto;
import com.youngstersclub.app.dto.WhatsappTemplateExecutionRecipientDto;
import com.youngstersclub.app.dto.WhatsappTemplateExecutionResultDto;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.UserRepository;
import com.youngstersclub.app.util.TimeUtil;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentDueReminderExecutor implements WhatsAppTemplateExecutor {

    private static final Logger log = LoggerFactory.getLogger(PaymentDueReminderExecutor.class);
    private static final String TEMPLATE_NAME = "payment_due_reminder";
    private static final BigDecimal DUE_THRESHOLD = new BigDecimal("500");

    private final UserRepository userRepository;
    private final UserPaymentSummaryService userPaymentSummaryService;
    private final WhatsAppService whatsAppService;
    private final BrevoEmailService brevoEmailService;

    public PaymentDueReminderExecutor(
            UserRepository userRepository,
            UserPaymentSummaryService userPaymentSummaryService,
            WhatsAppService whatsAppService,
            BrevoEmailService brevoEmailService) {
        this.userRepository = userRepository;
        this.userPaymentSummaryService = userPaymentSummaryService;
        this.whatsAppService = whatsAppService;
        this.brevoEmailService = brevoEmailService;
    }

    @Override
    public String getTemplateName() {
        return TEMPLATE_NAME;
    }

    @Override
    public WhatsappTemplateExecutionResultDto execute(boolean isDryRun) {
        LocalDateTime executionTime = TimeUtil.nowIST();
        List<User> activeCustomers = userRepository.findByRoleAndIsActiveTrue(UserRole.CUSTOMER);
        List<Integer> userIds = activeCustomers.stream()
                .map(User::getId)
                .toList();
        Map<Integer, UserPaymentSummaryDto> summariesByUserId = userPaymentSummaryService.getPaymentSummaries(userIds);

        List<WhatsappTemplateExecutionRecipientDto> eligibleRecipients = activeCustomers.stream()
                .map(user -> new WhatsappTemplateExecutionRecipientDto(
                        user.getId(),
                        user.getName(),
                        user.getPhone(),
                        summariesByUserId.getOrDefault(user.getId(), new UserPaymentSummaryDto(null, null, null)).getTotalDue()))
                .filter(recipient -> recipient.getAmount() != null && recipient.getAmount().compareTo(DUE_THRESHOLD) > 0)
                .sorted((left, right) -> right.getAmount().compareTo(left.getAmount()))
                .collect(Collectors.toList());

        int successCount = 0;
        int failedCount = 0;
        String mode = isDryRun ? "DRY RUN" : "ACTUAL RUN";
        log.info(
                "Payment due reminder job started. Mode: {}. Total customers scanned: {}, eligible customers: {}, skipped: {}",
                mode,
                activeCustomers.size(),
                eligibleRecipients.size(),
                Math.max(activeCustomers.size() - eligibleRecipients.size(), 0));

        for (WhatsappTemplateExecutionRecipientDto recipient : eligibleRecipients) {
            log.info(
                    "Payment due reminder eligible customer. userId: {}, name: {}, due: {}",
                    recipient.getUserId(),
                    recipient.getName(),
                    recipient.getAmount());

            if (isDryRun) {
                continue;
            }

            boolean sent = whatsAppService.sendPaymentDueReminderMessage(
                    recipient.getPhone(),
                    recipient.getName(),
                    recipient.getAmount(),
                    recipient.getUserId());
            if (sent) {
                successCount++;
            } else {
                failedCount++;
                log.warn("Payment due reminder failed or skipped for userId: {}", recipient.getUserId());
            }
        }

        WhatsappTemplateExecutionResultDto result = new WhatsappTemplateExecutionResultDto(
                TEMPLATE_NAME,
                isDryRun,
                executionTime,
                activeCustomers.size(),
                eligibleRecipients.size(),
                Math.max(activeCustomers.size() - eligibleRecipients.size(), 0),
                successCount,
                failedCount,
                new ArrayList<>(eligibleRecipients));

        sendSummaryEmail(result);

        log.info(
                "Payment due reminder job completed. Mode: {}. Total customers scanned: {}, eligible: {}, successful sends: {}, failed sends: {}",
                mode,
                result.getTotalCustomersScanned(),
                result.getEligibleCustomers(),
                result.getSuccessfulMessages(),
                result.getFailedMessages());

        return result;
    }

    private void sendSummaryEmail(WhatsappTemplateExecutionResultDto result) {
        try {
            List<String> adminEmails = userRepository.findByRoleInAndIsActiveTrue(List.of(UserRole.ADMIN, UserRole.SUPER_ADMIN))
                    .stream()
                    .map(User::getEmail)
                    .filter(email -> email != null && !email.isBlank())
                    .collect(Collectors.toList());
            int sentEmails = brevoEmailService.sendPaymentDueReminderSummaryEmail(result, adminEmails);
            log.info(
                    "Payment due reminder summary email completed. Mode: {}. Admin recipients emailed: {}",
                    result.isDryRun() ? "DRY RUN" : "ACTUAL RUN",
                    sentEmails);
        } catch (Exception ex) {
            log.error("Payment due reminder summary email failed. Reason: {}", ex.getMessage(), ex);
        }
    }
}
