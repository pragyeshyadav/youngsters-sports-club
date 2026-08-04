package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.BranchOptionDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
import com.youngstersclub.app.dto.TodayEarningsResponseDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.FrameRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.PaymentRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock private FrameRepository frameRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private GameActivityService gameActivityService;
    @Mock private UserRepository userRepository;
    @Mock private OrganizationContextService organizationContextService;
    @Mock private BranchRepository branchRepository;
    @Mock private OrganizationUserRepository organizationUserRepository;
    @Mock private UserBranchAccessRepository userBranchAccessRepository;

    @InjectMocks private AnalyticsService analyticsService;

    private User actor;
    private Organization organization;
    private Branch branch;
    private OrganizationUser membership;

    @BeforeEach
    void setUp() {
        actor = new User();
        actor.setId(14);
        actor.setEmail("manager@test.com");
        actor.setRole(UserRole.MANAGER);
        actor.setIsActive(true);

        organization = new Organization();
        organization.setId(1L);
        organization.setName("Youngsters Sports Club");
        organization.setIsActive(true);

        branch = new Branch();
        branch.setId(2L);
        branch.setName("Satna");
        branch.setOrganization(organization);
        branch.setIsActive(true);

        membership = new OrganizationUser();
        membership.setId(20L);
        membership.setOrganization(organization);
        membership.setUser(actor);
        membership.setRole(UserRole.MANAGER);
        membership.setBaseBranch(branch);
        membership.setIsActive(true);
        membership.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void getEarningsForDateReturnsOnlyCurrentBranchSettledPayments() {
        LocalDate selectedDate = LocalDate.of(2026, 7, 31);
        mockAuthorizedContext();
        when(frameRepository.findEarningsAnalyticsByBranchAndDateRange(branch.getId(), selectedDate.atStartOfDay(), selectedDate.plusDays(1).atStartOfDay()))
                .thenReturn(List.of());
        when(gameActivityService.getGrossEarningsBetween(selectedDate.atStartOfDay(), selectedDate.plusDays(1).atStartOfDay(), branch.getId()))
                .thenReturn(BigDecimal.ZERO);
        when(gameActivityService.getTotalUnpaidDueBetween(selectedDate.atStartOfDay(), selectedDate.plusDays(1).atStartOfDay(), branch.getId()))
                .thenReturn(BigDecimal.ZERO);
        when(gameActivityService.getUnpaidDueByUserForDate(selectedDate, branch.getId())).thenReturn(java.util.Map.of());

        PaymentRepository.SettledPaymentProjection projection = new PaymentRepository.SettledPaymentProjection() {
            @Override
            public String getUserName() {
                return "Pragyesh";
            }

            @Override
            public BigDecimal getPaidAmount() {
                return BigDecimal.valueOf(450);
            }

            @Override
            public BigDecimal getDiscount() {
                return BigDecimal.valueOf(50);
            }

            @Override
            public LocalDateTime getDate() {
                return selectedDate.atTime(12, 30);
            }

            @Override
            public String getPaymentMethod() {
                return "CASH";
            }
        };

        when(paymentRepository.findSettledPaymentsByBranchAndReferenceDateBetween(branch.getId(), selectedDate, selectedDate))
                .thenReturn(List.of(projection));

        TodayEarningsResponseDto response = analyticsService.getEarningsForDate(selectedDate, "manager@test.com");

        assertEquals(1, response.getSettledPayments().size());
        assertEquals("Pragyesh", response.getSettledPayments().get(0).getUserName());
        assertEquals("CASH", response.getSettledPayments().get(0).getPaymentMethod());
        verify(frameRepository).findEarningsAnalyticsByBranchAndDateRange(branch.getId(), selectedDate.atStartOfDay(), selectedDate.plusDays(1).atStartOfDay());
        verify(paymentRepository).findSettledPaymentsByBranchAndReferenceDateBetween(branch.getId(), selectedDate, selectedDate);
        verify(gameActivityService).getGrossEarningsBetween(selectedDate.atStartOfDay(), selectedDate.plusDays(1).atStartOfDay(), branch.getId());
        verify(gameActivityService).getTotalUnpaidDueBetween(selectedDate.atStartOfDay(), selectedDate.plusDays(1).atStartOfDay(), branch.getId());
        verify(gameActivityService).getUnpaidDueByUserForDate(selectedDate, branch.getId());
        verify(frameRepository, never()).findEarningsAnalyticsByDate(selectedDate);
        verify(frameRepository, never()).findTodayEarningsAnalytics();
    }

    @Test
    void getEarningsForDateRejectsUnauthorizedBranchContext() {
        OrganizationContextDto context = new OrganizationContextDto();
        context.setCurrentRole(UserRole.MANAGER.name());
        context.setCurrentOrganization(new OrganizationOptionDto(organization.getId(), organization.getName()));
        context.setCurrentBranch(new BranchOptionDto(branch.getId(), branch.getName()));

        when(userRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(actor));
        when(organizationContextService.resolveContext("manager@test.com")).thenReturn(context);
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
                .thenReturn(Optional.of(membership));
        membership.setBaseBranch(null);
        when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branch.getId(), organization.getId()))
                .thenReturn(Optional.of(branch));
        when(userBranchAccessRepository.existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(membership.getId(), branch.getId()))
                .thenReturn(false);

        SecurityException exception = assertThrows(
                SecurityException.class,
                () -> analyticsService.getEarningsForDate(LocalDate.of(2026, 7, 31), "manager@test.com"));

        assertEquals("You do not have access to the current branch", exception.getMessage());
    }

    private void mockAuthorizedContext() {
        OrganizationContextDto context = new OrganizationContextDto();
        context.setCurrentRole(UserRole.MANAGER.name());
        context.setCurrentOrganization(new OrganizationOptionDto(organization.getId(), organization.getName()));
        context.setCurrentBranch(new BranchOptionDto(branch.getId(), branch.getName()));
        context.setHasPersistedContext(true);
        context.setRequiresSelection(false);

        when(userRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(actor));
        when(organizationContextService.resolveContext("manager@test.com")).thenReturn(context);
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
                .thenReturn(Optional.of(membership));
        when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branch.getId(), organization.getId()))
                .thenReturn(Optional.of(branch));
    }
}
