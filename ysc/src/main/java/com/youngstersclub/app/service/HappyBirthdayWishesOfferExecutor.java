package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.WhatsappTemplateExecutionRecipientDto;
import com.youngstersclub.app.dto.WhatsappTemplateExecutionResultDto;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.ChildRepository;
import com.youngstersclub.app.repository.UserRepository;
import com.youngstersclub.app.util.TimeUtil;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class HappyBirthdayWishesOfferExecutor implements WhatsAppTemplateExecutor {

    private static final Logger log = LoggerFactory.getLogger(HappyBirthdayWishesOfferExecutor.class);
    private static final String TEMPLATE_NAME = "happy_birthday_wishes_offer";

    private final ChildRepository childRepository;
    private final UserRepository userRepository;
    private final WhatsAppService whatsAppService;
    private final BrevoEmailService brevoEmailService;

    public HappyBirthdayWishesOfferExecutor(
            ChildRepository childRepository,
            UserRepository userRepository,
            WhatsAppService whatsAppService,
            BrevoEmailService brevoEmailService) {
        this.childRepository = childRepository;
        this.userRepository = userRepository;
        this.whatsAppService = whatsAppService;
        this.brevoEmailService = brevoEmailService;
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

        List<WhatsappTemplateExecutionRecipientDto> recipients = birthdayChildren.stream()
                .map(child -> new WhatsappTemplateExecutionRecipientDto(
                        child.getParentUserId(),
                        child.getParentName(),
                        child.getParentPhone(),
                        BigDecimal.ZERO,
                        child.getChildName()))
                .toList();

        int successCount = 0;
        int failedCount = 0;
        String mode = isDryRun ? "DRY RUN" : "ACTUAL RUN";

        log.info(
                "Happy birthday wishes job started. Date: {}. Mode: {}. Today's birthdays: {}",
                today,
                mode,
                recipients.size());

        for (WhatsappTemplateExecutionRecipientDto recipient : recipients) {
            log.info(
                    "Happy birthday eligible parent. userId: {}, parentName: {}, kidName: {}",
                    recipient.getUserId(),
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
                    recipient.getUserId());
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

        sendSummaryEmail(result);

        log.info(
                "Happy birthday wishes job completed. Mode: {}. Today's birthdays: {}, successful sends: {}, failed sends: {}",
                mode,
                result.getTotalCustomersScanned(),
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
            int sentEmails = brevoEmailService.sendHappyBirthdayWishesSummaryEmail(result, adminEmails);
            log.info(
                    "Happy birthday wishes summary email completed. Mode: {}. Admin recipients emailed: {}",
                    result.isDryRun() ? "DRY RUN" : "ACTUAL RUN",
                    sentEmails);
        } catch (Exception ex) {
            log.error("Happy birthday wishes summary email failed. Reason: {}", ex.getMessage(), ex);
        }
    }
}
