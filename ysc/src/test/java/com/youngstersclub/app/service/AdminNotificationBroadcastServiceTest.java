package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.BranchOptionDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AdminNotificationBroadcastServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private WhatsAppService whatsAppService;
    @Mock private BrevoEmailService brevoEmailService;
    @Mock private OrganizationContextService organizationContextService;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationUserRepository organizationUserRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private UserBranchAccessRepository userBranchAccessRepository;
    @Mock private OrganizationSummaryRecipientService organizationSummaryRecipientService;

    @InjectMocks private AdminNotificationBroadcastService adminNotificationBroadcastService;

    private User actor;
    private Organization organization;
    private Branch satnaBranch;
    private OrganizationUser membership;

    @BeforeEach
    void setUp() {
        actor = new User();
        actor.setId(15);
        actor.setEmail("admin@test.com");
        actor.setIsActive(true);
        actor.setRole(UserRole.ADMIN);

        organization = new Organization();
        organization.setId(1L);
        organization.setName("Youngsters");
        organization.setEmail("org@test.com");
        organization.setPhone("9765657902");
        organization.setIsActive(true);

        satnaBranch = new Branch();
        satnaBranch.setId(2L);
        satnaBranch.setName("Rewa");
        satnaBranch.setOrganization(organization);
        satnaBranch.setIsActive(true);

        membership = new OrganizationUser();
        membership.setId(25L);
        membership.setUser(actor);
        membership.setOrganization(organization);
        membership.setRole(UserRole.ADMIN);
        membership.setBaseBranch(satnaBranch);
        membership.setIsActive(true);
    }

    @Test
    void searchCustomersUsesOrganizationAndSelectedBranchScope() {
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(actor));
        when(organizationContextService.resolveContext("admin@test.com")).thenReturn(buildContext(List.of(satnaBranch), satnaBranch));
        when(organizationRepository.findByIdAndIsActiveTrue(organization.getId())).thenReturn(Optional.of(organization));
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
                .thenReturn(Optional.of(membership));
        when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(satnaBranch.getId(), organization.getId()))
                .thenReturn(Optional.of(satnaBranch));

        List<UserSearchResultDto> expected = List.of(
                new UserSearchResultDto(101, "Prince", "prince@test.com", "gid", null, "9999999999", true, "CUSTOMER"));
        when(userRepository.searchActiveUserSummariesForOrganizationScope(
                eq("prin"),
                eq(""),
                eq(PageRequest.of(0, 10)),
                eq(organization.getId()),
                eq(satnaBranch.getId())))
                .thenReturn(expected);

        List<UserSearchResultDto> response =
                adminNotificationBroadcastService.searchCustomers("prin", "admin@test.com", satnaBranch.getId());

        assertSame(expected, response);
    }

    @Test
    void searchCustomersUsesOrganizationScopeWhenAllBranchesSelected() {
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(actor));
        when(organizationContextService.resolveContext("admin@test.com")).thenReturn(buildContext(List.of(satnaBranch), satnaBranch));
        when(organizationRepository.findByIdAndIsActiveTrue(organization.getId())).thenReturn(Optional.of(organization));
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
                .thenReturn(Optional.of(membership));

        List<UserSearchResultDto> expected = List.of(
                new UserSearchResultDto(102, "Rajneesh", "raj@test.com", "gid2", null, "8888888888", true, "CUSTOMER"));
        when(userRepository.searchActiveUserSummariesForOrganizationScope(
                eq("rajn"),
                eq(""),
                eq(PageRequest.of(0, 10)),
                eq(organization.getId()),
                eq(null)))
                .thenReturn(expected);

        List<UserSearchResultDto> response =
                adminNotificationBroadcastService.searchCustomers("rajn", "admin@test.com", null);

        assertSame(expected, response);
    }

    @Test
    void processNotificationBroadcastUsesAllBranchesWhenBranchIdIsNull() {
        User customer = buildCustomer(101, "Prince", "9999999999");
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(actor));
        when(organizationContextService.resolveContext("admin@test.com")).thenReturn(buildContext(List.of(satnaBranch), satnaBranch));
        when(organizationRepository.findByIdAndIsActiveTrue(organization.getId())).thenReturn(Optional.of(organization));
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
                .thenReturn(Optional.of(membership));
        when(userRepository.findActiveUsersByRoleAndOrganizationAndOptionalBranch(
                UserRole.CUSTOMER,
                organization.getId(),
                null))
                .thenReturn(List.of(customer));
        when(whatsAppService.sendClubCustomerNotificationMessage(
                "9999999999",
                "Prince",
                "Hi",
                "9765657902",
                "Youngsters",
                organization.getId(),
                null,
                "All Branches",
                101))
                .thenReturn(true);
        when(organizationSummaryRecipientService.resolveRecipientsForOrganization(organization.getId()))
                .thenReturn(List.of("pragyesh.yadav@gmail.com", "youngsterssportsclub@gmail.com"));
        when(brevoEmailService.sendNotificationBroadcastSummaryEmail(
                any(),
                any(),
                eq("org@test.com"),
                eq("All Customers"),
                eq("Hi"),
                eq(1),
                eq(0)))
                .thenReturn(1);

        adminNotificationBroadcastService.processNotificationBroadcast(
                "Hi",
                "ALL_CUSTOMERS",
                null,
                "admin@test.com",
                null);

        verify(userRepository).findActiveUsersByRoleAndOrganizationAndOptionalBranch(
                UserRole.CUSTOMER,
                organization.getId(),
                null);
        verify(organizationSummaryRecipientService).resolveRecipientsForOrganization(organization.getId());
    }

    @Test
    void processNotificationBroadcastUsesSelectedCustomersWithinSelectedBranchScope() {
        User customer = buildCustomer(101, "Prince", "9999999999");
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(actor));
        when(organizationContextService.resolveContext("admin@test.com")).thenReturn(buildContext(List.of(satnaBranch), satnaBranch));
        when(organizationRepository.findByIdAndIsActiveTrue(organization.getId())).thenReturn(Optional.of(organization));
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
                .thenReturn(Optional.of(membership));
        when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(satnaBranch.getId(), organization.getId()))
                .thenReturn(Optional.of(satnaBranch));
        when(userRepository.findActiveUsersByIdsAndRoleAndOrganizationAndOptionalBranch(
                List.of(101),
                UserRole.CUSTOMER,
                organization.getId(),
                satnaBranch.getId()))
                .thenReturn(List.of(customer));
        when(whatsAppService.sendClubCustomerNotificationMessage(
                "9999999999",
                "Prince",
                "Hi",
                "9765657902",
                "Youngsters",
                organization.getId(),
                satnaBranch.getId(),
                satnaBranch.getName(),
                101))
                .thenReturn(true);
        when(organizationSummaryRecipientService.resolveRecipientsForOrganization(organization.getId()))
                .thenReturn(List.of("pragyesh.yadav@gmail.com", "youngsterssportsclub@gmail.com"));
        when(brevoEmailService.sendNotificationBroadcastSummaryEmail(
                anyList(),
                anyList(),
                eq("org@test.com"),
                eq("Selected Customers"),
                eq("Hi"),
                eq(1),
                eq(0)))
                .thenReturn(1);

        adminNotificationBroadcastService.processNotificationBroadcast(
                "Hi",
                "SELECTED_CUSTOMERS",
                List.of(101),
                "admin@test.com",
                satnaBranch.getId());

        verify(userRepository).findActiveUsersByIdsAndRoleAndOrganizationAndOptionalBranch(
                List.of(101),
                UserRole.CUSTOMER,
                organization.getId(),
                satnaBranch.getId());
        verify(organizationSummaryRecipientService).resolveRecipientsForOrganization(organization.getId());
    }

    @Test
    void processNotificationBroadcastUsesOrganizationAwarePhoneFallbackWhenOrganizationPhoneMissing() {
        organization.setPhone("   ");
        User customer = buildCustomer(101, "Prince", "9999999999");

        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(actor));
        when(organizationContextService.resolveContext("admin@test.com")).thenReturn(buildContext(List.of(satnaBranch), satnaBranch));
        when(organizationRepository.findByIdAndIsActiveTrue(organization.getId())).thenReturn(Optional.of(organization));
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
                .thenReturn(Optional.of(membership));
        when(userRepository.findActiveUsersByRoleAndOrganizationAndOptionalBranch(
                UserRole.CUSTOMER,
                organization.getId(),
                null))
                .thenReturn(List.of(customer));
        when(whatsAppService.sendClubCustomerNotificationMessage(
                "9999999999",
                "Prince",
                "Hi",
                "   ",
                "Youngsters",
                organization.getId(),
                null,
                "All Branches",
                101))
                .thenReturn(true);
        when(organizationSummaryRecipientService.resolveRecipientsForOrganization(organization.getId()))
                .thenReturn(List.of("pragyesh.yadav@gmail.com"));
        when(brevoEmailService.sendNotificationBroadcastSummaryEmail(
                any(),
                any(),
                eq("org@test.com"),
                eq("All Customers"),
                eq("Hi"),
                eq(1),
                eq(0)))
                .thenReturn(1);

        adminNotificationBroadcastService.processNotificationBroadcast(
                "Hi",
                "ALL_CUSTOMERS",
                null,
                "admin@test.com",
                null);

        verify(whatsAppService).sendClubCustomerNotificationMessage(
                "9999999999",
                "Prince",
                "Hi",
                "   ",
                "Youngsters",
                organization.getId(),
                null,
                "All Branches",
                101);
    }

    @Test
    void processNotificationBroadcastRejectsInaccessibleBranch() {
        Branch rewaBranch = new Branch();
        rewaBranch.setId(3L);
        rewaBranch.setName("Satna");
        rewaBranch.setOrganization(organization);
        rewaBranch.setIsActive(true);

        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(actor));
        when(organizationContextService.resolveContext("admin@test.com")).thenReturn(buildContext(List.of(satnaBranch), satnaBranch));
        when(organizationRepository.findByIdAndIsActiveTrue(organization.getId())).thenReturn(Optional.of(organization));
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
                .thenReturn(Optional.of(membership));

        SecurityException exception = assertThrows(
                SecurityException.class,
                () -> adminNotificationBroadcastService.processNotificationBroadcast(
                        "Hi",
                        "ALL_CUSTOMERS",
                        null,
                        "admin@test.com",
                        rewaBranch.getId()));

        assertEquals("You do not have access to the selected branch", exception.getMessage());
        verify(userRepository, never()).findActiveUsersByRoleAndOrganizationAndOptionalBranch(any(), any(), any());
    }

    private OrganizationContextDto buildContext(List<Branch> accessibleBranches, Branch currentBranch) {
        OrganizationContextDto dto = new OrganizationContextDto();
        dto.setCurrentRole(UserRole.ADMIN.name());

        OrganizationOptionDto orgOption = new OrganizationOptionDto();
        orgOption.setId(organization.getId());
        orgOption.setName(organization.getName());
        dto.setCurrentOrganization(orgOption);

        BranchOptionDto currentBranchOption = new BranchOptionDto();
        currentBranchOption.setId(currentBranch.getId());
        currentBranchOption.setName(currentBranch.getName());
        dto.setCurrentBranch(currentBranchOption);

        dto.setAccessibleBranches(accessibleBranches.stream().map(branch -> {
            BranchOptionDto option = new BranchOptionDto();
            option.setId(branch.getId());
            option.setName(branch.getName());
            return option;
        }).toList());
        return dto;
    }

    private User buildCustomer(int id, String name, String phone) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setPhone(phone);
        user.setEmail(name.toLowerCase() + "@test.com");
        user.setRole(UserRole.CUSTOMER);
        user.setIsActive(true);
        return user;
    }
}
