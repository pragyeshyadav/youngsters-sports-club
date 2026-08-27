package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentDueReminderExecutorTest {

    @Mock private OrganizationUserRepository organizationUserRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private FrameRepository frameRepository;
    @Mock private ConsumableOrderRepository consumableOrderRepository;
    @Mock private KidsPlaySessionRepository kidsPlaySessionRepository;
    @Mock private GameActivityOrderRepository gameActivityOrderRepository;
    @Mock private PendingDueService pendingDueService;
    @Mock private WhatsAppService whatsAppService;
    @Mock private BrevoEmailService brevoEmailService;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationSummaryRecipientService organizationSummaryRecipientService;

    @InjectMocks private PaymentDueReminderExecutor executor;

    @Test
    void executeDryRunProcessesOrganizationsIncrementallyAndBuildsEligibleRecipients() {
        Long organizationId = 1L;
        when(organizationRepository.findByIdAndIsActiveTrue(organizationId))
                .thenReturn(Optional.of(organization(organizationId, "org@test.com")));
        when(organizationUserRepository.findDistinctActiveOrganizationIdsByRole(UserRole.CUSTOMER))
                .thenReturn(List.of(organizationId));
        when(organizationUserRepository.findActiveCustomerMembershipsByRoleAndOrganizationId(UserRole.CUSTOMER, organizationId))
                .thenReturn(List.of(
                        membership(organizationId, "YSC", 10, "Rahul", "9999999999", "Satna"),
                        membership(organizationId, "YSC", 11, "Prince", "8888888888", "Rewa")));

        when(frameRepository.getTotalDueForUsersByOrganization(List.of(10, 11), organizationId))
                .thenReturn(List.of(userDueProjection(10, BigDecimal.valueOf(400))));
        when(consumableOrderRepository.getTotalUnpaidDueByUserIdsAndOrganizationId(List.of(10, 11), organizationId))
                .thenReturn(List.of(userConsumableProjection(10, BigDecimal.valueOf(150))));
        when(kidsPlaySessionRepository.getTotalUnpaidDueByParentUserIdsAndOrganizationId(List.of(10, 11), organizationId))
                .thenReturn(List.of());
        when(gameActivityOrderRepository.getTotalUnpaidDueByParentUserIdsAndOrganizationId(List.of(10, 11), organizationId))
                .thenReturn(List.of(userActivityProjection(11, BigDecimal.valueOf(499))));

        Branch satna = new Branch();
        satna.setId(101L);
        satna.setName("Satna");
        Branch rewa = new Branch();
        rewa.setId(102L);
        rewa.setName("Rewa");
        when(branchRepository.findByOrganizationIdAndIsActiveTrueOrderByNameAsc(organizationId))
                .thenReturn(List.of(satna, rewa));

        when(frameRepository.getTotalDueForUsersByBranch(List.of(10), satna.getId()))
                .thenReturn(List.of(userDueProjection(10, BigDecimal.valueOf(550))));
        when(consumableOrderRepository.getTotalUnpaidDueByUserIdsAndBranchId(List.of(10), satna.getId()))
                .thenReturn(List.of());
        when(kidsPlaySessionRepository.getTotalUnpaidDueByParentUserIdsAndBranchId(List.of(10), satna.getId()))
                .thenReturn(List.of());
        when(gameActivityOrderRepository.getTotalUnpaidDueByParentUserIdsAndBranchId(List.of(10), satna.getId()))
                .thenReturn(List.of());

        when(frameRepository.getTotalDueForUsersByBranch(List.of(10), rewa.getId()))
                .thenReturn(List.of());
        when(consumableOrderRepository.getTotalUnpaidDueByUserIdsAndBranchId(List.of(10), rewa.getId()))
                .thenReturn(List.of());
        when(kidsPlaySessionRepository.getTotalUnpaidDueByParentUserIdsAndBranchId(List.of(10), rewa.getId()))
                .thenReturn(List.of());
        when(gameActivityOrderRepository.getTotalUnpaidDueByParentUserIdsAndBranchId(List.of(10), rewa.getId()))
                .thenReturn(List.of());

        when(organizationSummaryRecipientService.resolveRecipientsForOrganization(organizationId))
                .thenReturn(List.of("pragyesh.yadav@gmail.com", "youngsterssportsclub@gmail.com"));
        when(brevoEmailService.sendPaymentDueReminderSummaryEmail(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(0);

        WhatsappTemplateExecutionResultDto result = executor.execute(true);

        assertEquals(2, result.getTotalCustomersScanned());
        assertEquals(1, result.getEligibleCustomers());
        assertEquals(1, result.getSkippedCustomers());
        assertEquals(0, result.getSuccessfulMessages());
        assertEquals(0, result.getFailedMessages());
        assertEquals(1, result.getRecipients().size());
        assertEquals("Rahul", result.getRecipients().get(0).getName());
        assertEquals(new BigDecimal("550"), result.getRecipients().get(0).getAmount());
        assertEquals("Satna", result.getRecipients().get(0).getBranchName());
        verify(whatsAppService, never()).sendPaymentDueReminderMessage(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt());
        verify(brevoEmailService).sendPaymentDueReminderSummaryEmail(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.eq("org@test.com"));
        verify(organizationSummaryRecipientService).resolveRecipientsForOrganization(organizationId);
        verify(branchRepository).findByOrganizationIdAndIsActiveTrueOrderByNameAsc(organizationId);
        verify(pendingDueService, never()).calculateCustomerDue(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void resolveDueBranchNamesByUserFallsBackToBaseBranchWhenNoBranchTotalsFound() {
        Long organizationId = 1L;
        when(branchRepository.findByOrganizationIdAndIsActiveTrueOrderByNameAsc(organizationId))
                .thenReturn(List.of());

        Map<Integer, String> result = executor.resolveDueBranchNamesByUser(
                organizationId,
                List.of(membership(organizationId, "YSC", 10, "Rahul", "9999999999", "Satna")));

        assertEquals("Satna", result.get(10));
    }

    @Test
    void loadBranchTotalDueByUserIdMergesAllModuleTotals() {
        List<Integer> userIds = List.of(10, 11);
        Long branchId = 101L;
        when(frameRepository.getTotalDueForUsersByBranch(userIds, branchId))
                .thenReturn(List.of(userDueProjection(10, BigDecimal.valueOf(100))));
        when(consumableOrderRepository.getTotalUnpaidDueByUserIdsAndBranchId(userIds, branchId))
                .thenReturn(List.of(userConsumableProjection(10, BigDecimal.valueOf(20))));
        when(kidsPlaySessionRepository.getTotalUnpaidDueByParentUserIdsAndBranchId(userIds, branchId))
                .thenReturn(List.of(userKidsProjection(11, BigDecimal.valueOf(30))));
        when(gameActivityOrderRepository.getTotalUnpaidDueByParentUserIdsAndBranchId(userIds, branchId))
                .thenReturn(List.of(userActivityProjection(10, BigDecimal.valueOf(5))));

        Map<Integer, BigDecimal> totals = executor.loadBranchTotalDueByUserId(userIds, branchId);

        assertEquals(new BigDecimal("125"), totals.get(10));
        assertEquals(new BigDecimal("30"), totals.get(11));
    }

    private Organization organization(Long id, String email) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setEmail(email);
        organization.setIsActive(true);
        return organization;
    }

    private OrganizationUserRepository.ActiveCustomerMembershipProjection membership(
            Long organizationId,
            String organizationName,
            Integer userId,
            String userName,
            String phone,
            String baseBranchName) {
        return new OrganizationUserRepository.ActiveCustomerMembershipProjection() {
            @Override
            public Long getOrganizationId() {
                return organizationId;
            }

            @Override
            public String getOrganizationName() {
                return organizationName;
            }

            @Override
            public Integer getUserId() {
                return userId;
            }

            @Override
            public String getUserName() {
                return userName;
            }

            @Override
            public String getPhone() {
                return phone;
            }

            @Override
            public String getBaseBranchName() {
                return baseBranchName;
            }
        };
    }

    private FrameRepository.UserDueProjection userDueProjection(Integer userId, BigDecimal amount) {
        return new FrameRepository.UserDueProjection() {
            @Override
            public Integer getUserId() {
                return userId;
            }

            @Override
            public BigDecimal getAmount() {
                return amount;
            }
        };
    }

    private ConsumableOrderRepository.UserConsumableDueProjection userConsumableProjection(Integer userId, BigDecimal amount) {
        return new ConsumableOrderRepository.UserConsumableDueProjection() {
            @Override
            public Integer getUserId() {
                return userId;
            }

            @Override
            public BigDecimal getAmount() {
                return amount;
            }
        };
    }

    private KidsPlaySessionRepository.UserKidsDueProjection userKidsProjection(Integer userId, BigDecimal amount) {
        return new KidsPlaySessionRepository.UserKidsDueProjection() {
            @Override
            public Integer getUserId() {
                return userId;
            }

            @Override
            public BigDecimal getAmount() {
                return amount;
            }
        };
    }

    private GameActivityOrderRepository.UserActivityDueProjection userActivityProjection(Integer userId, BigDecimal amount) {
        return new GameActivityOrderRepository.UserActivityDueProjection() {
            @Override
            public Integer getUserId() {
                return userId;
            }

            @Override
            public BigDecimal getAmount() {
                return amount;
            }
        };
    }
}
