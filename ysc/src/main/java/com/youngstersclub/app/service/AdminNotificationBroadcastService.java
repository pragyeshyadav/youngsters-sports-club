package com.youngstersclub.app.service;

import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.UserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AdminNotificationBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(AdminNotificationBroadcastService.class);

    private final UserRepository userRepository;
    private final WhatsAppService whatsAppService;
    private final BrevoEmailService brevoEmailService;

    public AdminNotificationBroadcastService(
            UserRepository userRepository,
            WhatsAppService whatsAppService,
            BrevoEmailService brevoEmailService) {
        this.userRepository = userRepository;
        this.whatsAppService = whatsAppService;
        this.brevoEmailService = brevoEmailService;
    }

    @Async
    public void triggerNotificationBroadcast(String message, String recipientType, List<Integer> customerIds) {
        try {
            processNotificationBroadcast(message, recipientType, customerIds);
        } catch (Exception ex) {
            log.error("Notification broadcast failed. recipientType: {}. Reason: {}", recipientType, ex.getMessage(), ex);
        }
    }

    public void processNotificationBroadcast(String message, String recipientType, List<Integer> customerIds) {
        String normalizedMessage = message == null ? "" : message.trim();
        if (normalizedMessage.isBlank()) {
            throw new IllegalArgumentException("Notification message is required");
        }

        RecipientType resolvedRecipientType = RecipientType.from(recipientType);
        List<User> recipients = resolveRecipients(resolvedRecipientType, customerIds);
        List<User> uniqueRecipients = deduplicateRecipients(recipients);

        int successCount = 0;
        int failedCount = 0;
        log.info(
                "Starting WhatsApp notification broadcast. recipientType: {}, totalRecipients: {}",
                resolvedRecipientType,
                uniqueRecipients.size());

        for (User recipient : uniqueRecipients) {
            try {
                boolean sent = whatsAppService.sendClubCustomerNotificationMessage(
                        recipient.getPhone(),
                        recipient.getName(),
                        normalizedMessage,
                        recipient.getId());
                if (sent) {
                    successCount++;
                } else {
                    failedCount++;
                    log.warn("Notification broadcast message failed or skipped for userId: {}", recipient.getId());
                }
            } catch (Exception ex) {
                failedCount++;
                log.warn("Notification broadcast failed for userId: {}. Reason: {}", recipient.getId(), ex.getMessage(), ex);
            }
        }

        sendBroadcastSummary(uniqueRecipients, resolvedRecipientType, normalizedMessage, successCount, failedCount);

        log.info(
                "Completed WhatsApp notification broadcast. recipientType: {}, recipients: {}, successfulSends: {}, failedSends: {}",
                resolvedRecipientType,
                uniqueRecipients.size(),
                successCount,
                failedCount);
    }

    private List<User> resolveRecipients(RecipientType recipientType, List<Integer> customerIds) {
        return switch (recipientType) {
            case SNOOKER_PLAYERS -> userRepository.findDistinctUsersWithFrameParticipation(UserRole.CUSTOMER);
            case ALL_CUSTOMERS -> userRepository.findByRoleAndIsActiveTrue(UserRole.CUSTOMER);
            case SELECTED_CUSTOMERS -> {
                if (customerIds == null || customerIds.isEmpty()) {
                    throw new IllegalArgumentException("At least one selected customer is required");
                }
                if (customerIds.size() > 20) {
                    throw new IllegalArgumentException("You can select up to 20 customers");
                }
                yield userRepository.findByIdInAndRoleAndIsActiveTrue(customerIds, UserRole.CUSTOMER);
            }
        };
    }

    private List<User> deduplicateRecipients(List<User> recipients) {
        Map<Integer, User> byId = new LinkedHashMap<>();
        for (User user : recipients == null ? List.<User>of() : recipients) {
            if (user == null || user.getId() == null) {
                continue;
            }
            byId.putIfAbsent(user.getId(), user);
        }
        return List.copyOf(byId.values());
    }

    private void sendBroadcastSummary(
            List<User> recipients,
            RecipientType recipientType,
            String message,
            int successCount,
            int failedCount) {
        try {
            List<String> adminEmails = userRepository.findByRoleInAndIsActiveTrue(List.of(UserRole.ADMIN, UserRole.SUPER_ADMIN))
                    .stream()
                    .map(User::getEmail)
                    .filter(email -> email != null && !email.isBlank())
                    .collect(Collectors.toList());

            int emailSentCount = brevoEmailService.sendNotificationBroadcastSummaryEmail(
                    recipients,
                    adminEmails,
                    recipientType.toDisplayLabel(),
                    message,
                    successCount,
                    failedCount);

            log.info(
                    "Notification broadcast summary email completed. recipientType: {}, adminRecipientsEmailed: {}",
                    recipientType,
                    emailSentCount);
        } catch (Exception ex) {
            log.error("Notification broadcast summary email failed. Reason: {}", ex.getMessage(), ex);
        }
    }

    private enum RecipientType {
        SNOOKER_PLAYERS,
        ALL_CUSTOMERS,
        SELECTED_CUSTOMERS;

        private static RecipientType from(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Recipient type is required");
            }
            return RecipientType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }

        private String toDisplayLabel() {
            return switch (this) {
                case SNOOKER_PLAYERS -> "Snooker Players";
                case ALL_CUSTOMERS -> "All Customers";
                case SELECTED_CUSTOMERS -> "Selected Customers";
            };
        }
    }
}
