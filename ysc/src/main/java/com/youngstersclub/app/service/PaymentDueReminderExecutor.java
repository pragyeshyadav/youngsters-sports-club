package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.UserPaymentSummaryDto;
import com.youngstersclub.app.dto.WhatsappTemplateExecutionRecipientDto;
import com.youngstersclub.app.dto.WhatsappTemplateExecutionResultDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.OrganizationUser;
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
import java.util.LinkedHashSet;
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
        List<OrganizationUser> customerMemberships = organizationUserRepository.findByRoleAndIsActiveTrue(UserRole.CUSTOMER)
                .stream()
                .filter(membership -> membership.getUser() != null
                        && Boolean.TRUE.equals(membership.getUser().getIsActive())
                        && membership.getOrganization() != null
                        && Boolean.TRUE.equals(membership.getOrganization().getIsActive()))
                .toList();

        Map<Long, List<OrganizationUser>> membershipsByOrganizationId = customerMemberships.stream()
                .collect(Collectors.groupingBy(
                        membership -> membership.getOrganization().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<WhatsappTemplateExecutionRecipientDto> eligibleRecipients = new ArrayList<>();
        int totalCustomersScanned = 0;

        for (Map.Entry<Long, List<OrganizationUser>> entry : membershipsByOrganizationId.entrySet()) {
            Long organizationId = entry.getKey();
            List<OrganizationUser> organizationMemberships = entry.getValue();
            List<Integer> userIds = organizationMemberships.stream()
                    .map(membership -> membership.getUser().getId())
                    .distinct()
                    .toList();
            if (userIds.isEmpty()) {
                continue;
            }

            Map<Integer, BigDecimal> totalDueByUserId = new LinkedHashMap<>();
            frameRepository.getTotalDueForUsersByOrganization(userIds, organizationId)
                    .forEach(projection -> totalDueByUserId.merge(
                            projection.getUserId(),
                            projection.getAmount() == null ? BigDecimal.ZERO : projection.getAmount(),
                            BigDecimal::add));
            consumableOrderRepository.getTotalUnpaidDueByUserIdsAndOrganizationId(userIds, organizationId)
                    .forEach(projection -> totalDueByUserId.merge(
                            projection.getUserId(),
                            projection.getAmount() == null ? BigDecimal.ZERO : projection.getAmount(),
                            BigDecimal::add));
            kidsPlaySessionRepository.getTotalUnpaidDueByParentUserIdsAndOrganizationId(userIds, organizationId)
                    .forEach(projection -> totalDueByUserId.merge(
                            projection.getUserId(),
                            projection.getAmount() == null ? BigDecimal.ZERO : projection.getAmount(),
                            BigDecimal::add));
            gameActivityOrderRepository.getTotalUnpaidDueByParentUserIdsAndOrganizationId(userIds, organizationId)
                    .forEach(projection -> totalDueByUserId.merge(
                            projection.getUserId(),
                            projection.getAmount() == null ? BigDecimal.ZERO : projection.getAmount(),
                            BigDecimal::add));

            Map<Long, List<Branch>> branchesByOrganization = Map.of(
                    organizationId,
                    branchRepository.findByOrganizationIdAndIsActiveTrueOrderByNameAsc(organizationId));
            totalCustomersScanned += organizationMemberships.size();

            for (OrganizationUser membership : organizationMemberships) {
                Integer userId = membership.getUser().getId();
                BigDecimal totalDue = totalDueByUserId.getOrDefault(userId, BigDecimal.ZERO);
                if (totalDue.compareTo(DUE_THRESHOLD) <= 0) {
                    continue;
                }

                String dueBranchNames = resolveDueBranchNames(userId.longValue(), branchesByOrganization.get(organizationId), membership);
                eligibleRecipients.add(new WhatsappTemplateExecutionRecipientDto(
                        userId,
                        membership.getUser().getName(),
                        membership.getUser().getPhone(),
                        totalDue,
                        null,
                        membership.getOrganization().getName(),
                        dueBranchNames,
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

    private String resolveDueBranchNames(Long userId, List<Branch> organizationBranches, OrganizationUser membership) {
        LinkedHashSet<String> dueBranchNames = new LinkedHashSet<>();
        if (organizationBranches != null) {
            for (Branch branch : organizationBranches) {
                if (branch == null || branch.getId() == null) {
                    continue;
                }
                if (pendingDueService.calculateCustomerDue(userId, branch.getId()).totalDue().compareTo(BigDecimal.ZERO) > 0) {
                    dueBranchNames.add(branch.getName());
                }
            }
        }
        if (dueBranchNames.isEmpty() && membership.getBaseBranch() != null && membership.getBaseBranch().getName() != null) {
            dueBranchNames.add(membership.getBaseBranch().getName());
        }
        return dueBranchNames.isEmpty() ? "Organization-wide" : String.join(", ", dueBranchNames);
    }
}
