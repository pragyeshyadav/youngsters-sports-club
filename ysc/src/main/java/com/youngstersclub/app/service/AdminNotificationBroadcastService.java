package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.UserSearchResultDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.OrganizationRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AdminNotificationBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(AdminNotificationBroadcastService.class);

    private final UserRepository userRepository;
    private final WhatsAppService whatsAppService;
    private final BrevoEmailService brevoEmailService;
    private final OrganizationContextService organizationContextService;
    private final OrganizationRepository organizationRepository;
    private final OrganizationUserRepository organizationUserRepository;
    private final BranchRepository branchRepository;
    private final UserBranchAccessRepository userBranchAccessRepository;
    private final OrganizationSummaryRecipientService organizationSummaryRecipientService;

    public AdminNotificationBroadcastService(
            UserRepository userRepository,
            WhatsAppService whatsAppService,
            BrevoEmailService brevoEmailService,
            OrganizationContextService organizationContextService,
            OrganizationRepository organizationRepository,
            OrganizationUserRepository organizationUserRepository,
            BranchRepository branchRepository,
            UserBranchAccessRepository userBranchAccessRepository,
            OrganizationSummaryRecipientService organizationSummaryRecipientService) {
        this.userRepository = userRepository;
        this.whatsAppService = whatsAppService;
        this.brevoEmailService = brevoEmailService;
        this.organizationContextService = organizationContextService;
        this.organizationRepository = organizationRepository;
        this.organizationUserRepository = organizationUserRepository;
        this.branchRepository = branchRepository;
        this.userBranchAccessRepository = userBranchAccessRepository;
        this.organizationSummaryRecipientService = organizationSummaryRecipientService;
    }

    @Async
    public void triggerNotificationBroadcast(
            String message,
            String recipientType,
            List<Integer> customerIds,
            String actorEmail,
            Long branchId) {
        try {
            processNotificationBroadcast(message, recipientType, customerIds, actorEmail, branchId);
        } catch (Exception ex) {
            log.error("Notification broadcast failed. recipientType: {}. Reason: {}", recipientType, ex.getMessage(), ex);
        }
    }

    public void processNotificationBroadcast(
            String message,
            String recipientType,
            List<Integer> customerIds,
            String actorEmail,
            Long branchId) {
        String normalizedMessage = message == null ? "" : message.trim();
        if (normalizedMessage.isBlank()) {
            throw new IllegalArgumentException("Notification message is required");
        }

        NotificationScope scope = resolveNotificationScope(actorEmail, branchId);
        RecipientType resolvedRecipientType = RecipientType.from(recipientType);
        List<User> recipients = resolveRecipients(resolvedRecipientType, customerIds, scope);
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
                        scope.organizationPhone(),
                        scope.organizationName(),
                        scope.organizationId(),
                        scope.branchId(),
                        scope.branchLabel(),
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

        sendBroadcastSummary(
                uniqueRecipients,
                scope.organizationId(),
                scope.organizationEmail(),
                resolvedRecipientType,
                normalizedMessage,
                successCount,
                failedCount);

        log.info(
                "Completed WhatsApp notification broadcast. recipientType: {}, recipients: {}, successfulSends: {}, failedSends: {}",
                resolvedRecipientType,
                uniqueRecipients.size(),
                successCount,
                failedCount);
    }

    public List<UserSearchResultDto> searchCustomers(String query, String actorEmail, Long branchId) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            return List.of();
        }

        String digitsQuery = normalizedQuery.replaceAll("\\D", "");
        if (normalizedQuery.length() < 3 && digitsQuery.length() < 3) {
            return List.of();
        }

        NotificationScope scope = resolveNotificationScope(actorEmail, branchId);
        return userRepository.searchActiveUserSummariesForOrganizationScope(
                normalizedQuery,
                digitsQuery,
                PageRequest.of(0, 10),
                scope.organizationId(),
                scope.branchId());
    }

    protected List<User> resolveRecipients(
            RecipientType recipientType,
            List<Integer> customerIds,
            NotificationScope scope) {
        return switch (recipientType) {
            case SNOOKER_PLAYERS -> userRepository.findDistinctUsersWithFrameParticipationByRoleAndOrganizationAndOptionalBranch(
                    UserRole.CUSTOMER,
                    scope.organizationId(),
                    scope.branchId());
            case ALL_CUSTOMERS -> userRepository.findActiveUsersByRoleAndOrganizationAndOptionalBranch(
                    UserRole.CUSTOMER,
                    scope.organizationId(),
                    scope.branchId());
            case SELECTED_CUSTOMERS -> {
                if (customerIds == null || customerIds.isEmpty()) {
                    throw new IllegalArgumentException("At least one selected customer is required");
                }
                if (customerIds.size() > 20) {
                    throw new IllegalArgumentException("You can select up to 20 customers");
                }
                yield userRepository.findActiveUsersByIdsAndRoleAndOrganizationAndOptionalBranch(
                        customerIds,
                        UserRole.CUSTOMER,
                        scope.organizationId(),
                        scope.branchId());
            }
        };
    }

    protected List<User> deduplicateRecipients(List<User> recipients) {
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
            Long organizationId,
            String organizationEmail,
            RecipientType recipientType,
            String message,
            int successCount,
            int failedCount) {
        try {
            List<String> adminEmails = organizationSummaryRecipientService.resolveRecipientsForOrganization(organizationId);

            int emailSentCount = brevoEmailService.sendNotificationBroadcastSummaryEmail(
                    recipients,
                    adminEmails,
                    organizationEmail,
                    recipientType.toDisplayLabel(),
                    message,
                    successCount,
                    failedCount);

            log.info(
                    "Notification broadcast summary email completed. organizationId: {}, recipientType: {}, adminRecipientsEmailed: {}",
                    organizationId,
                    recipientType,
                    emailSentCount);
        } catch (Exception ex) {
            log.error("Notification broadcast summary email failed. Reason: {}", ex.getMessage(), ex);
        }
    }

    protected NotificationScope resolveNotificationScope(String actorEmail, Long requestedBranchId) {
        String normalizedEmail = actorEmail == null ? "" : actorEmail.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.isEmpty()) {
            throw new SecurityException("Authenticated user email is required");
        }

        User actor = userRepository.findByEmail(normalizedEmail)
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .orElseThrow(() -> new SecurityException("Authenticated user not found"));

        OrganizationContextDto context = organizationContextService.resolveContext(normalizedEmail);
        if (context.getCurrentOrganization() == null || context.getAccessibleBranches() == null) {
            throw new IllegalArgumentException("Current organization context is required");
        }

        Long organizationId = context.getCurrentOrganization().getId();
        Organization organization = organizationRepository.findByIdAndIsActiveTrue(organizationId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Current organization not found"));
        String organizationEmail = organization.getEmail();
        OrganizationUser membership = organizationUserRepository
                .findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organizationId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Caller organization membership not found"));

        List<Long> accessibleBranchIds = context.getAccessibleBranches() == null
                ? List.of()
                : context.getAccessibleBranches().stream()
                        .map(branch -> branch == null ? null : branch.getId())
                        .filter(id -> id != null)
                        .toList();

        if (requestedBranchId == null) {
            return new NotificationScope(
                    organizationId,
                    organization.getName(),
                    organization.getPhone(),
                    organizationEmail,
                    null,
                    "All Branches");
        }

        if (!accessibleBranchIds.contains(requestedBranchId)) {
            throw new SecurityException("You do not have access to the selected branch");
        }

        Branch branch = branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(requestedBranchId, organizationId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Selected branch not found"));

        boolean branchAccessible = membership.getBaseBranch() != null
                && requestedBranchId.equals(membership.getBaseBranch().getId());
        if (!branchAccessible) {
            branchAccessible = userBranchAccessRepository.existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(
                    membership.getId(),
                    requestedBranchId);
        }

        if (!branchAccessible) {
            throw new SecurityException("You do not have access to the selected branch");
        }

        return new NotificationScope(
                organizationId,
                organization.getName(),
                organization.getPhone(),
                organizationEmail,
                branch.getId(),
                branch.getName());
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

    protected record NotificationScope(
            Long organizationId,
            String organizationName,
            String organizationPhone,
            String organizationEmail,
            Long branchId,
            String branchLabel) {
    }
}
