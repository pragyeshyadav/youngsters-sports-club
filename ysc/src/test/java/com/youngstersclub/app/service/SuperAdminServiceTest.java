package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.SuperAdminOrganizationDto;
import com.youngstersclub.app.dto.SuperAdminOrganizationRequest;
import com.youngstersclub.app.dto.SuperAdminBranchRequest;
import com.youngstersclub.app.dto.SuperAdminStaffAssignmentRequest;
import com.youngstersclub.app.dto.SuperAdminStaffAssignmentResponseDto;
import com.youngstersclub.app.dto.SuperAdminUserSearchResultDto;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SuperAdminServiceTest {

  @Mock private SuperAdminAuthorizationService superAdminAuthorizationService;
  @Mock private UserRepository userRepository;
  @Mock private OrganizationRepository organizationRepository;
  @Mock private BranchRepository branchRepository;
  @Mock private OrganizationUserRepository organizationUserRepository;
  @Mock private UserBranchAccessRepository userBranchAccessRepository;

  @InjectMocks private SuperAdminService superAdminService;

  private User actor;
  private User targetUser;
  private Organization organization;
  private Organization secondOrganization;
  private Branch rewaBranch;
  private Branch satnaBranch;

  @BeforeEach
  void setUp() {
    actor = new User();
    actor.setId(2);
    actor.setEmail("superadmin@example.com");
    actor.setRole(UserRole.SUPER_ADMIN);
    actor.setIsActive(true);

    organization = new Organization();
    organization.setId(10L);
    organization.setName("Headquarter City Center Snooker Club");
    organization.setIsActive(true);

    secondOrganization = new Organization();
    secondOrganization.setId(11L);
    secondOrganization.setName("Area 7 Snooker Club");
    secondOrganization.setIsActive(true);

    rewaBranch = new Branch();
    rewaBranch.setId(101L);
    rewaBranch.setOrganization(organization);
    rewaBranch.setName("Rewa");
    rewaBranch.setIsActive(true);

    satnaBranch = new Branch();
    satnaBranch.setId(102L);
    satnaBranch.setOrganization(organization);
    satnaBranch.setName("Satna");
    satnaBranch.setIsActive(true);

    targetUser = new User();
    targetUser.setId(44);
    targetUser.setName("Existing Customer");
    targetUser.setEmail("customer@example.com");
    targetUser.setRole(UserRole.CUSTOMER);
    targetUser.setIsActive(true);
  }

  @Test
  void createOrganizationNormalizesFieldsAndPersistsActiveRecord() {
    SuperAdminOrganizationRequest request = new SuperAdminOrganizationRequest();
    request.setActorEmail(" superadmin@example.com ");
    request.setName("  New Club  ");
    request.setLogoUrl(" https://cdn.example.com/logo.png ");
    request.setPhone("9876543210");
    request.setEmail(" INFO@EXAMPLE.COM ");
    request.setAddress(" Main Road ");
    request.setCity(" Rewa ");
    request.setState(" Madhya Pradesh ");
    request.setCountry(" India ");

    when(superAdminAuthorizationService.requireSuperAdmin("superadmin@example.com")).thenReturn(actor);
    when(organizationRepository.save(any(Organization.class))).thenAnswer(invocation -> {
      Organization saved = invocation.getArgument(0);
      saved.setId(99L);
      return saved;
    });

    SuperAdminOrganizationDto response = superAdminService.createOrganization(request);

    assertEquals(99L, response.getId());
    assertEquals("New Club", response.getName());
    assertEquals("info@example.com", response.getEmail());
    assertEquals("Rewa", response.getCity());
    assertTrue(response.isActive());

    ArgumentCaptor<Organization> organizationCaptor = ArgumentCaptor.forClass(Organization.class);
    verify(organizationRepository).save(organizationCaptor.capture());
    assertNotNull(organizationCaptor.getValue().getCreatedAt());
    assertNotNull(organizationCaptor.getValue().getUpdatedAt());
  }

  @Test
  void resolveAssignableRoleRejectsUnsupportedRole() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> superAdminService.resolveAssignableRole("SUPER_ADMIN"));

    assertEquals("Assigned role is invalid", exception.getMessage());
  }

  @Test
  void normalizePhoneReturnsNullWhenBlankAndRejectsInvalidLength() {
    assertNull(superAdminService.normalizePhone("   "));

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> superAdminService.normalizePhone("12345"));

    assertEquals("Phone number must be exactly 10 digits", exception.getMessage());
  }

  @Test
  void searchCustomersReturnsEmptyWhenQueryTooShort() {
    when(superAdminAuthorizationService.requireSuperAdmin("superadmin@example.com")).thenReturn(actor);

    List<?> response = superAdminService.searchCustomers("superadmin@example.com", organization.getId(), "ab");

    assertTrue(response.isEmpty());
    verify(userRepository, never()).searchActiveUserSummariesForOrganizationScopeAndRole(
        any(), any(), any(), any(), any(), any());
  }

  @Test
  void searchCustomersUsesOrganizationScopedRoleSearchWithoutBranchFiltering() {
    when(superAdminAuthorizationService.requireSuperAdmin("superadmin@example.com")).thenReturn(actor);
    when(userRepository.searchActiveUserSummariesForOrganizationScopeAndRole(
        eq("pragye"),
        eq(""),
        any(),
        eq(organization.getId()),
        eq(null),
        eq(UserRole.CUSTOMER)))
        .thenReturn(List.of(new UserSearchResultDto(
            44,
            "Pragyesh Yadav",
            "pragyesh@example.com",
            "gid-1",
            "profile.png",
            "9876543210",
            true,
            "CUSTOMER")));

    List<SuperAdminUserSearchResultDto> response =
        superAdminService.searchCustomers("superadmin@example.com", organization.getId(), "pragye");

    assertEquals(1, response.size());
    assertEquals(44, response.get(0).getId());
    assertEquals("Pragyesh Yadav", response.get(0).getName());
    verify(userRepository).searchActiveUserSummariesForOrganizationScopeAndRole(
        eq("pragye"),
        eq(""),
        any(),
        eq(organization.getId()),
        eq(null),
        eq(UserRole.CUSTOMER));
  }

  @Test
  void deactivateBranchRejectsActiveBaseBranchUsage() {
    when(superAdminAuthorizationService.requireSuperAdmin("superadmin@example.com")).thenReturn(actor);
    when(branchRepository.findByIdAndOrganizationId(rewaBranch.getId(), organization.getId()))
        .thenReturn(Optional.of(rewaBranch));
    when(organizationUserRepository.countByBaseBranchIdAndIsActiveTrue(rewaBranch.getId()))
        .thenReturn(1L);

    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> superAdminService.deactivateBranch(rewaBranch.getId(), organization.getId(), "superadmin@example.com"));

    assertEquals("Branch is still assigned as a base branch for active users", exception.getMessage());
  }

  @Test
  void upsertStaffAssignmentPreservesGlobalAdminRoleWhenAnotherAdminMembershipExists() {
    SuperAdminStaffAssignmentRequest request = new SuperAdminStaffAssignmentRequest();
    request.setActorEmail("superadmin@example.com");
    request.setUserId(targetUser.getId());
    request.setOrganizationId(organization.getId());
    request.setRole("MANAGER");
    request.setBaseBranchId(rewaBranch.getId());
    request.setBranchIds(List.of(rewaBranch.getId(), satnaBranch.getId()));

    OrganizationUser existingMembership = buildMembership(700L, targetUser, organization, satnaBranch, UserRole.CUSTOMER, true);
    OrganizationUser otherAdminMembership = buildMembership(701L, targetUser, secondOrganization, satnaBranch, UserRole.ADMIN, true);

    when(superAdminAuthorizationService.requireSuperAdmin("superadmin@example.com")).thenReturn(actor);
    when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));
    when(organizationRepository.findByIdAndIsActiveTrue(organization.getId())).thenReturn(Optional.of(organization));
    when(branchRepository.findByOrganizationIdAndIdInAndIsActiveTrue(organization.getId(), List.of(rewaBranch.getId(), satnaBranch.getId())))
        .thenReturn(List.of(rewaBranch, satnaBranch));
    when(organizationUserRepository.findByUserIdAndOrganizationId(targetUser.getId(), organization.getId()))
        .thenReturn(Optional.of(existingMembership));
    when(organizationUserRepository.save(any(OrganizationUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(userBranchAccessRepository.findByOrganizationUserId(existingMembership.getId())).thenReturn(List.of());
    when(userBranchAccessRepository.findByOrganizationUserIdAndIsActiveTrue(existingMembership.getId())).thenReturn(List.of());
    when(organizationUserRepository.findByUserIdAndIsActiveTrue(targetUser.getId()))
        .thenReturn(List.of(existingMembership, otherAdminMembership));

    SuperAdminStaffAssignmentResponseDto response = superAdminService.upsertStaffAssignment(request);

    assertEquals("MANAGER", response.getAssignedRole());
    assertEquals("Rewa", response.getBaseBranchName());
    assertFalse(response.isMembershipCreated());
    assertEquals(UserRole.ADMIN, targetUser.getRole());

    verify(userRepository).save(targetUser);
  }

  @Test
  void upsertStaffAssignmentCreatesMembershipAndBranchAccessForNewStaff() {
    SuperAdminStaffAssignmentRequest request = new SuperAdminStaffAssignmentRequest();
    request.setActorEmail("superadmin@example.com");
    request.setUserId(targetUser.getId());
    request.setOrganizationId(organization.getId());
    request.setRole("ADMIN");
    request.setBaseBranchId(rewaBranch.getId());
    request.setBranchIds(List.of(rewaBranch.getId(), satnaBranch.getId()));

    when(superAdminAuthorizationService.requireSuperAdmin("superadmin@example.com")).thenReturn(actor);
    when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));
    when(organizationRepository.findByIdAndIsActiveTrue(organization.getId())).thenReturn(Optional.of(organization));
    when(branchRepository.findByOrganizationIdAndIdInAndIsActiveTrue(organization.getId(), List.of(rewaBranch.getId(), satnaBranch.getId())))
        .thenReturn(List.of(rewaBranch, satnaBranch));
    when(organizationUserRepository.findByUserIdAndOrganizationId(targetUser.getId(), organization.getId()))
        .thenReturn(Optional.empty());
    when(organizationUserRepository.save(any(OrganizationUser.class))).thenAnswer(invocation -> {
      OrganizationUser membership = invocation.getArgument(0);
      if (membership.getId() == null) {
        membership.setId(800L);
      }
      return membership;
    });
    when(userBranchAccessRepository.findByOrganizationUserId(800L)).thenReturn(List.of());
    when(userBranchAccessRepository.findByOrganizationUserIdAndIsActiveTrue(800L)).thenReturn(List.of());
    when(organizationUserRepository.findByUserIdAndIsActiveTrue(targetUser.getId()))
        .thenReturn(List.of(buildMembership(800L, targetUser, organization, rewaBranch, UserRole.ADMIN, true)));

    SuperAdminStaffAssignmentResponseDto response = superAdminService.upsertStaffAssignment(request);

    assertTrue(response.isMembershipCreated());
    assertEquals("ADMIN", response.getAssignedRole());
    assertEquals(1, response.getActiveBranches().size());
    assertEquals("Rewa", response.getActiveBranches().get(0).getName());
    assertEquals(UserRole.ADMIN, targetUser.getRole());
    verify(userRepository).save(targetUser);
  }

  private OrganizationUser buildMembership(
      Long id,
      User user,
      Organization org,
      Branch baseBranch,
      UserRole role,
      boolean active) {
    OrganizationUser membership = new OrganizationUser();
    membership.setId(id);
    membership.setUser(user);
    membership.setOrganization(org);
    membership.setBaseBranch(baseBranch);
    membership.setRole(role);
    membership.setIsActive(active);
    membership.setCreatedAt(LocalDateTime.now().minusDays(1));
    return membership;
  }

  @Test
  void applyBranchRequestMapsEditableFields() {
    Branch branch = new Branch();
    SuperAdminBranchRequest request = new SuperAdminBranchRequest();
    request.setName("  Main Branch ");
    request.setBranchCode(" HQ01 ");
    request.setAddress(" Center Road ");
    request.setCity(" Satna ");
    request.setState(" MP ");
    request.setPhone("9876543210");
    request.setEmail(" branch@example.com ");
    request.setLatitude(new BigDecimal("24.1234567"));
    request.setLongitude(new BigDecimal("80.1234567"));

    superAdminService.applyBranchRequest(branch, request);

    assertEquals("Main Branch", branch.getName());
    assertEquals("HQ01", branch.getBranchCode());
    assertEquals("branch@example.com", branch.getEmail());
    assertEquals(new BigDecimal("24.1234567"), branch.getLatitude());
  }
}
