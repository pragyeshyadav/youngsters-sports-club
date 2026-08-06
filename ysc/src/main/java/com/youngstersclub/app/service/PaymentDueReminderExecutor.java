package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.WhatsappTemplateExecutionRecipientDto;
import com.youngstersclub.app.dto.WhatsappTemplateExecutionResultDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.ConsumableOrderRepository;
import com.youngstersclub.app.repository.FrameRepository;
import com.youngstersclub.app.repository.GameActivityOrderRepository;
import com.youngstersclub.app.repository.KidsPlaySessionRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.UserRepository;
import com.youngstersclub.app.util.TimeUtil;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private final OrganizationUserRepository organizationUserRepository;
    private final BranchRepository branchRepository;
    private final FrameRepository frameRepository;
    private final ConsumableOrderRepository consumableOrderRepository;
    private final KidsPlaySessionRepository kidsPlaySessionRepository;
    private final GameActivityOrderRepository gameActivityOrderRepository;
    private final PendingDueService pendingDueService;
    private final WhatsAppService whatsAppService;
    private final BrevoEmailService brevoEmailService;

    public PaymentDueReminderExecutor(
            UserRepository userRepository,
            OrganizationUserRepository organizationUserRepository,
            BranchRepository branchRepository,
            FrameRepository frameRepository,
            ConsumableOrderRepository consumableOrderRepository,
            KidsPlaySessionRepository kidsPlaySessionRepository,
            GameActivityOrderRepository gameActivityOrderRepository,
            PendingDueService pendingDueService,
            WhatsAppService whatsAppService,
            BrevoEmailService brevoEmailService) {
        this.userRepository = userRepository;
        this.organizationUserRepository = organizationUserRepository;
        this.branchRepository = branchRepository;
        this.frameRepository = frameRepository;
        this.consumableOrderRepository = consumableOrderRepository;
        this.kidsPlaySessionRepository = kidsPlaySessionRepository;
        this.gameActivityOrderRepository = gameActivityOrderRepository;
        this.pendingDueService = pendingDueService;
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
        List<WhatsappTemplateExecutionRecipientDto> eligibleRecipients = new ArrayList<>();
        int totalCustomersScanned = 0;

        for (Long organizationId : organizationUserRepository.findDistinctActiveOrganizationIdsByRole(UserRole.CUSTOMER)) {
            List<OrganizationUserRepository.ActiveCustomerMembershipProjection> organizationMemberships =
                    organizationUserRepository.findActiveCustomerMembershipsByRoleAndOrganizationId(
                            UserRole.CUSTOMER,
                            organizationId);
            List<Integer> userIds = extractDistinctUserIds(organizationMemberships);
            if (userIds.isEmpty()) {
                continue;
            }

            Map<Integer, BigDecimal> totalDueByUserId = loadOrganizationTotalDueByUserId(userIds, organizationId);
            totalCustomersScanned += organizationMemberships.size();
            List<OrganizationUserRepository.ActiveCustomerMembershipProjection> eligibleMemberships = organizationMemberships.stream()
                    .filter(membership -> totalDueByUserId
                            .getOrDefault(membership.getUserId(), BigDecimal.ZERO)
                            .compareTo(DUE_THRESHOLD) > 0)
                    .toList();
            if (eligibleMemberships.isEmpty()) {
                continue;
            }

            Map<Integer, String> branchNamesByUserId = resolveDueBranchNamesByUser(organizationId, eligibleMemberships);

            for (OrganizationUserRepository.ActiveCustomerMembershipProjection membership : eligibleMemberships) {
                Integer userId = membership.getUserId();
                BigDecimal totalDue = totalDueByUserId.getOrDefault(userId, BigDecimal.ZERO);
                eligibleRecipients.add(new WhatsappTemplateExecutionRecipientDto(
                        userId,
                        membership.getUserName(),
                        membership.getPhone(),
                        totalDue,
                        null,
                        membership.getOrganizationName(),
                        branchNamesByUserId.getOrDefault(userId, fallbackBranchName(membership)),
                        "TOTAL DUE ABOVE ₹500"));
            }
        }

        eligibleRecipients = eligibleRecipients.stream()
                .sorted((left, right) -> right.getAmount().compareTo(left.getAmount()))
                .toList();

        int successCount = 0;
        int failedCount = 0;
        String mode = isDryRun ? "DRY RUN" : "ACTUAL RUN";
        log.info(
                "Payment due reminder job started. Mode: {}. Total customers scanned: {}, eligible customers: {}, skipped: {}",
                mode,
                totalCustomersScanned,
                eligibleRecipients.size(),
                Math.max(totalCustomersScanned - eligibleRecipients.size(), 0));

        for (WhatsappTemplateExecutionRecipientDto recipient : eligibleRecipients) {
            log.info(
                    "Payment due reminder eligible customer. userId: {}, organization: {}, branches: {}, due: {}",
                    recipient.getUserId(),
                    recipient.getOrganizationName(),
                    recipient.getBranchName(),
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
                totalCustomersScanned,
                eligibleRecipients.size(),
                Math.max(totalCustomersScanned - eligibleRecipients.size(), 0),
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

    protected List<Integer> extractDistinctUserIds(
            List<OrganizationUserRepository.ActiveCustomerMembershipProjection> memberships) {
        if (memberships == null || memberships.isEmpty()) {
            return List.of();
        }
        return memberships.stream()
                .map(OrganizationUserRepository.ActiveCustomerMembershipProjection::getUserId)
                .filter(userId -> userId != null)
                .distinct()
                .toList();
    }

    protected Map<Integer, BigDecimal> loadOrganizationTotalDueByUserId(List<Integer> userIds, Long organizationId) {
        Map<Integer, BigDecimal> totalDueByUserId = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty() || organizationId == null) {
            return totalDueByUserId;
        }

        frameRepository.getTotalDueForUsersByOrganization(userIds, organizationId)
                .forEach(projection -> mergeDueAmount(totalDueByUserId, projection.getUserId(), projection.getAmount()));
        consumableOrderRepository.getTotalUnpaidDueByUserIdsAndOrganizationId(userIds, organizationId)
                .forEach(projection -> mergeDueAmount(totalDueByUserId, projection.getUserId(), projection.getAmount()));
        kidsPlaySessionRepository.getTotalUnpaidDueByParentUserIdsAndOrganizationId(userIds, organizationId)
                .forEach(projection -> mergeDueAmount(totalDueByUserId, projection.getUserId(), projection.getAmount()));
        gameActivityOrderRepository.getTotalUnpaidDueByParentUserIdsAndOrganizationId(userIds, organizationId)
                .forEach(projection -> mergeDueAmount(totalDueByUserId, projection.getUserId(), projection.getAmount()));
        return totalDueByUserId;
    }

    protected Map<Integer, String> resolveDueBranchNamesByUser(
            Long organizationId,
            List<OrganizationUserRepository.ActiveCustomerMembershipProjection> eligibleMemberships) {
        Map<Integer, String> dueBranchNamesByUserId = new LinkedHashMap<>();
        List<Integer> eligibleUserIds = extractDistinctUserIds(eligibleMemberships);
        if (organizationId == null || eligibleUserIds.isEmpty()) {
            return dueBranchNamesByUserId;
        }

        Map<Integer, String> fallbackBranchNameByUserId = eligibleMemberships.stream()
                .collect(Collectors.toMap(
                        OrganizationUserRepository.ActiveCustomerMembershipProjection::getUserId,
                        this::fallbackBranchName,
                        (left, right) -> left,
                        LinkedHashMap::new));

        for (Branch branch : branchRepository.findByOrganizationIdAndIsActiveTrueOrderByNameAsc(organizationId)) {
            if (branch == null || branch.getId() == null || branch.getName() == null || branch.getName().isBlank()) {
                continue;
            }
            Map<Integer, BigDecimal> branchDueByUserId = loadBranchTotalDueByUserId(eligibleUserIds, branch.getId());
            for (Map.Entry<Integer, BigDecimal> entry : branchDueByUserId.entrySet()) {
                if (entry.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                dueBranchNamesByUserId.merge(
                        entry.getKey(),
                        branch.getName(),
                        (existing, ignored) -> existing + ", " + branch.getName());
            }
        }

        fallbackBranchNameByUserId.forEach((userId, fallbackBranchName) ->
                dueBranchNamesByUserId.putIfAbsent(userId, fallbackBranchName));
        return dueBranchNamesByUserId;
    }

    protected Map<Integer, BigDecimal> loadBranchTotalDueByUserId(List<Integer> userIds, Long branchId) {
        Map<Integer, BigDecimal> totalDueByUserId = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty() || branchId == null) {
            return totalDueByUserId;
        }

        frameRepository.getTotalDueForUsersByBranch(userIds, branchId)
                .forEach(projection -> mergeDueAmount(totalDueByUserId, projection.getUserId(), projection.getAmount()));
        consumableOrderRepository.getTotalUnpaidDueByUserIdsAndBranchId(userIds, branchId)
                .forEach(projection -> mergeDueAmount(totalDueByUserId, projection.getUserId(), projection.getAmount()));
        kidsPlaySessionRepository.getTotalUnpaidDueByParentUserIdsAndBranchId(userIds, branchId)
                .forEach(projection -> mergeDueAmount(totalDueByUserId, projection.getUserId(), projection.getAmount()));
        gameActivityOrderRepository.getTotalUnpaidDueByParentUserIdsAndBranchId(userIds, branchId)
                .forEach(projection -> mergeDueAmount(totalDueByUserId, projection.getUserId(), projection.getAmount()));
        return totalDueByUserId;
    }

    protected void mergeDueAmount(Map<Integer, BigDecimal> totalsByUserId, Integer userId, BigDecimal amount) {
        if (totalsByUserId == null || userId == null) {
            return;
        }
        totalsByUserId.merge(userId, amount == null ? BigDecimal.ZERO : amount, BigDecimal::add);
    }

    protected String fallbackBranchName(OrganizationUserRepository.ActiveCustomerMembershipProjection membership) {
        if (membership == null) {
            return "Organization-wide";
        }
        String baseBranchName = membership.getBaseBranchName();
        return (baseBranchName == null || baseBranchName.isBlank()) ? "Organization-wide" : baseBranchName;
    }
}
