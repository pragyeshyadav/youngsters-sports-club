package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.WhatsappTemplateExecutionRecipientDto;
import com.youngstersclub.app.dto.WhatsappTemplateExecutionResultDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.ConsumableOrderRepository;
import com.youngstersclub.app.repository.FrameRepository;
import com.youngstersclub.app.repository.GameActivityOrderRepository;
import com.youngstersclub.app.repository.KidsPlaySessionRepository;
import com.youngstersclub.app.repository.OrganizationRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
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

    private final OrganizationUserRepository organizationUserRepository;
    private final BranchRepository branchRepository;
    private final FrameRepository frameRepository;
    private final ConsumableOrderRepository consumableOrderRepository;
    private final KidsPlaySessionRepository kidsPlaySessionRepository;
    private final GameActivityOrderRepository gameActivityOrderRepository;
    private final PendingDueService pendingDueService;
    private final WhatsAppService whatsAppService;
    private final BrevoEmailService brevoEmailService;
    private final OrganizationRepository organizationRepository;
    private final OrganizationSummaryRecipientService organizationSummaryRecipientService;

    public PaymentDueReminderExecutor(
            OrganizationUserRepository organizationUserRepository,
            BranchRepository branchRepository,
            FrameRepository frameRepository,
            ConsumableOrderRepository consumableOrderRepository,
            KidsPlaySessionRepository kidsPlaySessionRepository,
            GameActivityOrderRepository gameActivityOrderRepository,
            PendingDueService pendingDueService,
            WhatsAppService whatsAppService,
            BrevoEmailService brevoEmailService,
            OrganizationRepository organizationRepository,
            OrganizationSummaryRecipientService organizationSummaryRecipientService) {
        this.organizationUserRepository = organizationUserRepository;
        this.branchRepository = branchRepository;
        this.frameRepository = frameRepository;
        this.consumableOrderRepository = consumableOrderRepository;
        this.kidsPlaySessionRepository = kidsPlaySessionRepository;
        this.gameActivityOrderRepository = gameActivityOrderRepository;
        this.pendingDueService = pendingDueService;
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
        LocalDateTime executionTime = TimeUtil.nowIST();
        String mode = isDryRun ? "DRY RUN" : "ACTUAL RUN";
        List<WhatsappTemplateExecutionResultDto> organizationResults = organizationUserRepository
                .findDistinctActiveOrganizationIdsByRole(UserRole.CUSTOMER)
                .stream()
                .map(organizationId -> executeForOrganizationInternal(organizationId, isDryRun, executionTime))
                .toList();

        WhatsappTemplateExecutionResultDto result = buildCombinedResult(organizationResults);

        log.info(
                "Payment due reminder job completed. Mode: {}. Total customers scanned: {}, eligible: {}, successful sends: {}, failed sends: {}",
                mode,
                result.getTotalCustomersScanned(),
                result.getEligibleCustomers(),
                result.getSuccessfulMessages(),
                result.getFailedMessages());

        return result;
    }

    @Override
    public WhatsappTemplateExecutionResultDto executeForOrganization(Long organizationId, boolean isDryRun) {
        return executeForOrganizationInternal(organizationId, isDryRun, TimeUtil.nowIST());
    }

    protected WhatsappTemplateExecutionResultDto executeForOrganizationInternal(
            Long organizationId,
            boolean isDryRun,
            LocalDateTime executionTime) {
        List<OrganizationUserRepository.ActiveCustomerMembershipProjection> organizationMemberships =
                organizationUserRepository.findActiveCustomerMembershipsByRoleAndOrganizationId(
                        UserRole.CUSTOMER,
                        organizationId);
        List<Integer> userIds = extractDistinctUserIds(organizationMemberships);
        if (userIds.isEmpty()) {
            WhatsappTemplateExecutionResultDto emptyResult = new WhatsappTemplateExecutionResultDto(
                    TEMPLATE_NAME,
                    isDryRun,
                    executionTime,
                    organizationMemberships.size(),
                    0,
                    organizationMemberships.size(),
                    0,
                    0,
                    List.of());
            sendSummaryEmail(organizationId, emptyResult, resolveOrganizationEmail(organizationId));
            return emptyResult;
        }

        Map<Integer, BigDecimal> totalDueByUserId = loadOrganizationTotalDueByUserId(userIds, organizationId);
        List<OrganizationUserRepository.ActiveCustomerMembershipProjection> eligibleMemberships = organizationMemberships.stream()
                .filter(membership -> totalDueByUserId
                        .getOrDefault(membership.getUserId(), BigDecimal.ZERO)
                        .compareTo(DUE_THRESHOLD) > 0)
                .toList();

        Map<Integer, String> branchNamesByUserId = resolveDueBranchNamesByUser(organizationId, eligibleMemberships);
        List<WhatsappTemplateExecutionRecipientDto> eligibleRecipients = buildEligibleRecipients(
                eligibleMemberships,
                totalDueByUserId,
                branchNamesByUserId);

        int successCount = 0;
        int failedCount = 0;
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
                    organizationId,
                    resolveBaseBranchId(eligibleMemberships, recipient.getUserId()),
                    recipient.getBranchName(),
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
                organizationMemberships.size(),
                eligibleRecipients.size(),
                Math.max(organizationMemberships.size() - eligibleRecipients.size(), 0),
                successCount,
                failedCount,
                new ArrayList<>(eligibleRecipients));

        sendSummaryEmail(organizationId, result, resolveOrganizationEmail(organizationId));
        return result;
    }

    protected List<WhatsappTemplateExecutionRecipientDto> buildEligibleRecipients(
            List<OrganizationUserRepository.ActiveCustomerMembershipProjection> eligibleMemberships,
            Map<Integer, BigDecimal> totalDueByUserId,
            Map<Integer, String> branchNamesByUserId) {
        return (eligibleMemberships == null ? List.<OrganizationUserRepository.ActiveCustomerMembershipProjection>of() : eligibleMemberships)
                .stream()
                .map(membership -> {
                    Integer userId = membership.getUserId();
                    BigDecimal totalDue = totalDueByUserId.getOrDefault(userId, BigDecimal.ZERO);
                    return new WhatsappTemplateExecutionRecipientDto(
                            userId,
                            membership.getUserName(),
                            membership.getPhone(),
                            totalDue,
                            null,
                            membership.getOrganizationName(),
                            branchNamesByUserId.getOrDefault(userId, fallbackBranchName(membership)),
                            "TOTAL DUE ABOVE ₹500");
                })
                .sorted((left, right) -> right.getAmount().compareTo(left.getAmount()))
                .toList();
    }

    protected WhatsappTemplateExecutionResultDto buildCombinedResult(List<WhatsappTemplateExecutionResultDto> organizationResults) {
        List<WhatsappTemplateExecutionRecipientDto> eligibleRecipients = new ArrayList<>();
        int totalCustomersScanned = 0;
        int eligibleCustomers = 0;
        int skippedCustomers = 0;
        int successCount = 0;
        int failedCount = 0;
        boolean isDryRun = false;
        LocalDateTime executionTime = TimeUtil.nowIST();

        for (WhatsappTemplateExecutionResultDto result : organizationResults == null ? List.<WhatsappTemplateExecutionResultDto>of() : organizationResults) {
            if (result == null) {
                continue;
            }
            isDryRun = result.isDryRun();
            executionTime = result.getExecutionTime();
            totalCustomersScanned += result.getTotalCustomersScanned();
            eligibleCustomers += result.getEligibleCustomers();
            skippedCustomers += result.getSkippedCustomers();
            successCount += result.getSuccessfulMessages();
            failedCount += result.getFailedMessages();
            if (result.getRecipients() != null) {
                eligibleRecipients.addAll(result.getRecipients());
            }
        }

        return new WhatsappTemplateExecutionResultDto(
                TEMPLATE_NAME,
                isDryRun,
                executionTime,
                totalCustomersScanned,
                eligibleCustomers,
                skippedCustomers,
                successCount,
                failedCount,
                new ArrayList<>(eligibleRecipients));
    }

    private void sendSummaryEmail(Long organizationId, WhatsappTemplateExecutionResultDto result, String organizationEmail) {
        try {
            List<String> adminEmails = organizationSummaryRecipientService.resolveRecipientsForOrganization(organizationId);
            int sentEmails = brevoEmailService.sendPaymentDueReminderSummaryEmail(result, adminEmails, organizationEmail);
            log.info(
                    "Payment due reminder summary email completed. organizationId: {}, mode: {}. Admin recipients emailed: {}",
                    organizationId,
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

    protected Long resolveBaseBranchId(
            List<OrganizationUserRepository.ActiveCustomerMembershipProjection> memberships,
            Integer userId) {
        if (memberships == null || userId == null) {
            return null;
        }
        return memberships.stream()
                .filter(membership -> userId.equals(membership.getUserId()))
                .map(OrganizationUserRepository.ActiveCustomerMembershipProjection::getBaseBranchId)
                .filter(branchId -> branchId != null)
                .findFirst()
                .orElse(null);
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

    protected String resolveOrganizationEmail(Long organizationId) {
        return organizationRepository.findByIdAndIsActiveTrue(organizationId)
                .map(Organization::getEmail)
                .orElse(null);
    }
}
