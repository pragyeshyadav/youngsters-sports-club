package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.AdminMonthlyEarningsDto;
import com.youngstersclub.app.dto.BranchOptionDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.ConsumableOrderRepository;
import com.youngstersclub.app.repository.FrameRepository;
import com.youngstersclub.app.repository.KidsPlaySessionRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.math.BigDecimal;
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
class AdminAnalyticsServiceTest {

    @Mock private FrameRepository frameRepository;
    @Mock private ConsumableOrderRepository consumableOrderRepository;
    @Mock private KidsPlaySessionRepository kidsPlaySessionRepository;
    @Mock private GameActivityService gameActivityService;
    @Mock private UserRepository userRepository;
    @Mock private OrganizationContextService organizationContextService;
    @Mock private BranchRepository branchRepository;
    @Mock private OrganizationUserRepository organizationUserRepository;
    @Mock private UserBranchAccessRepository userBranchAccessRepository;

    @InjectMocks private AdminAnalyticsService adminAnalyticsService;

    private User actor;
    private Organization organization;
    private Branch branch;
    private OrganizationUser membership;

    @BeforeEach
    void setUp() {
        actor = new User();
        actor.setId(14);
        actor.setEmail("admin@test.com");
        actor.setRole(UserRole.ADMIN);
        actor.setIsActive(true);

        organization = new Organization();
        organization.setId(9L);
        organization.setName("Area 7 Snooker Club");
        organization.setIsActive(true);

        branch = new Branch();
        branch.setId(5L);
        branch.setName("Rewa");
        branch.setOrganization(organization);
        branch.setIsActive(true);

        membership = new OrganizationUser();
        membership.setId(20L);
        membership.setOrganization(organization);
        membership.setUser(actor);
        membership.setRole(UserRole.ADMIN);
        membership.setBaseBranch(branch);
        membership.setIsActive(true);
        membership.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void getMonthlyEarningsUsesOnlyCurrentBranchScopedQueries() {
        mockAuthorizedContext();

        when(frameRepository.getCompletedEarningsBetweenAndBranchId(
                        eq(branch.getId()), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(BigDecimal.valueOf(1000), BigDecimal.valueOf(200));
        when(consumableOrderRepository.getPaidEarningsBetweenAndBranchId(
                        eq(branch.getId()), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(BigDecimal.valueOf(300), BigDecimal.valueOf(50));
        when(kidsPlaySessionRepository.getPaidEarningsBetweenAndBranchId(
                        eq(branch.getId()), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(BigDecimal.valueOf(150), BigDecimal.valueOf(25));
        when(gameActivityService.getPaidEarningsBetween(
                        any(LocalDateTime.class), any(LocalDateTime.class), eq(branch.getId())))
                .thenReturn(BigDecimal.valueOf(75), BigDecimal.valueOf(10));
        when(frameRepository.getCompletedEarningsByTableBetweenAndBranchId(
                        eq(branch.getId()), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        tableRow("Area 7 Arena", BigDecimal.valueOf(800)),
                        tableRow("Area 7 Practice", BigDecimal.valueOf(200))));

        AdminMonthlyEarningsDto response = adminAnalyticsService.getMonthlyEarnings(8, 2026, "admin@test.com");

        assertEquals(new BigDecimal("1525"), response.getCurrentMonthTotal());
        assertEquals(new BigDecimal("285"), response.getPreviousMonthTotal());
        assertEquals(new BigDecimal("1000"), response.getSnookerEarnings());
        assertEquals(new BigDecimal("300"), response.getConsumableEarnings());
        assertEquals(new BigDecimal("225"), response.getKidsZoneEarnings());
        assertEquals(2, response.getSnookerTableBreakdown().size());
        assertEquals(new BigDecimal("800"), response.getSnookerTableBreakdown().get("Area 7 Arena"));

        verify(frameRepository, never()).getCompletedEarningsBetween(any(LocalDateTime.class), any(LocalDateTime.class));
        verify(frameRepository, never()).getCompletedEarningsByTableBetween(any(LocalDateTime.class), any(LocalDateTime.class));
        verify(consumableOrderRepository, never()).getPaidEarningsBetween(any(LocalDateTime.class), any(LocalDateTime.class));
        verify(kidsPlaySessionRepository, never()).getPaidEarningsBetween(any(LocalDateTime.class), any(LocalDateTime.class));
        verify(gameActivityService, never()).getPaidEarningsBetween(any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void getMonthlyEarningsReturnsZeroValuesWhenCurrentBranchHasNoData() {
        mockAuthorizedContext();

        when(frameRepository.getCompletedEarningsBetweenAndBranchId(
                        eq(branch.getId()), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(null, null);
        when(consumableOrderRepository.getPaidEarningsBetweenAndBranchId(
                        eq(branch.getId()), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(null, null);
        when(kidsPlaySessionRepository.getPaidEarningsBetweenAndBranchId(
                        eq(branch.getId()), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(null, null);
        when(gameActivityService.getPaidEarningsBetween(
                        any(LocalDateTime.class), any(LocalDateTime.class), eq(branch.getId())))
                .thenReturn(BigDecimal.ZERO, BigDecimal.ZERO);
        when(frameRepository.getCompletedEarningsByTableBetweenAndBranchId(
                        eq(branch.getId()), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        AdminMonthlyEarningsDto response = adminAnalyticsService.getMonthlyEarnings(8, 2026, "admin@test.com");

        assertEquals(BigDecimal.ZERO, response.getCurrentMonthTotal());
        assertEquals(BigDecimal.ZERO, response.getPreviousMonthTotal());
        assertEquals(BigDecimal.ZERO, response.getSnookerEarnings());
        assertEquals(BigDecimal.ZERO, response.getConsumableEarnings());
        assertEquals(BigDecimal.ZERO, response.getKidsZoneEarnings());
        assertEquals(0, response.getSnookerTableBreakdown().size());
    }

    @Test
    void getMonthlyEarningsRejectsUnauthorizedBranchContext() {
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(actor));
        when(organizationContextService.resolveContext("admin@test.com")).thenReturn(buildContext());
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
                .thenReturn(Optional.of(membership));
        membership.setBaseBranch(null);
        when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branch.getId(), organization.getId()))
                .thenReturn(Optional.of(branch));
        when(userBranchAccessRepository.existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(membership.getId(), branch.getId()))
                .thenReturn(false);

        SecurityException exception = assertThrows(
                SecurityException.class,
                () -> adminAnalyticsService.getMonthlyEarnings(8, 2026, "admin@test.com"));

        assertEquals("You do not have access to the current branch", exception.getMessage());
    }

    private void mockAuthorizedContext() {
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(actor));
        when(organizationContextService.resolveContext("admin@test.com")).thenReturn(buildContext());
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
                .thenReturn(Optional.of(membership));
        when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branch.getId(), organization.getId()))
                .thenReturn(Optional.of(branch));
    }

    private OrganizationContextDto buildContext() {
        OrganizationContextDto context = new OrganizationContextDto();
        context.setCurrentRole(UserRole.ADMIN.name());
        context.setCurrentOrganization(new OrganizationOptionDto(organization.getId(), organization.getName()));
        context.setCurrentBranch(new BranchOptionDto(branch.getId(), branch.getName()));
        context.setHasPersistedContext(true);
        context.setRequiresSelection(false);
        return context;
    }

    private FrameRepository.SnookerTableEarningsProjection tableRow(String tableName, BigDecimal total) {
        return new FrameRepository.SnookerTableEarningsProjection() {
            @Override
            public String getTableName() {
                return tableName;
            }

            @Override
            public BigDecimal getTotal() {
                return total;
            }
        };
    }
}
