package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.BranchAccessUpdateRequest;
import com.youngstersclub.app.dto.BranchOptionDto;
import com.youngstersclub.app.dto.ManagerAdminDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
import com.youngstersclub.app.dto.PromoteManagerRequest;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.entity.UserBranchAccess;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
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

@ExtendWith(MockitoExtension.class)
class ManagerAdminServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private OrganizationUserRepository organizationUserRepository;
  @Mock private UserBranchAccessRepository userBranchAccessRepository;
  @Mock private BranchRepository branchRepository;
  @Mock private OrganizationContextService organizationContextService;

  @InjectMocks private ManagerAdminService managerAdminService;

  private User admin;
  private Organization organization;
  private Branch satna;
  private Branch rewa;
  private OrganizationUser adminMembership;

  @BeforeEach
  void setUp() {
    admin = new User();
    admin.setId(14);
    admin.setEmail("admin@test.com");
    admin.setRole(UserRole.ADMIN);
    admin.setIsActive(true);

    organization = new Organization();
    organization.setId(1L);
    organization.setName("Youngsters Sports Club");
    organization.setIsActive(true);

    satna = new Branch();
    satna.setId(2L);
    satna.setName("Satna");
    satna.setOrganization(organization);
    satna.setIsActive(true);

    rewa = new Branch();
    rewa.setId(3L);
    rewa.setName("Rewa");
    rewa.setOrganization(organization);
    rewa.setIsActive(true);

    adminMembership = new OrganizationUser();
    adminMembership.setId(20L);
    adminMembership.setOrganization(organization);
    adminMembership.setUser(admin);
    adminMembership.setRole(UserRole.ADMIN);
    adminMembership.setBaseBranch(satna);
    adminMembership.setIsActive(true);
  }

  @Test
  void promoteSetsManagerRoleAndGrantsCurrentBranchAccess() {
    mockAuthorizedContext();

    User customer = buildCustomer(25, "Rahul");
    OrganizationUser customerMembership = buildMembership(41L, customer, UserRole.CUSTOMER);

    when(userRepository.findById(25)).thenReturn(Optional.of(customer));
    when(organizationUserRepository.findByUserIdAndOrganizationId(customer.getId(), organization.getId()))
        .thenReturn(Optional.of(customerMembership));
    when(userBranchAccessRepository.findByOrganizationUserIdAndBranchId(41L, satna.getId()))
        .thenReturn(Optional.empty());
    when(userBranchAccessRepository.save(any(UserBranchAccess.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(organizationUserRepository.save(any(OrganizationUser.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    PromoteManagerRequest request = new PromoteManagerRequest();
    request.setUserId(25);
    ManagerAdminDto result = managerAdminService.promoteManager(request, "admin@test.com");

    assertEquals(UserRole.MANAGER.name(), result.getRole());
    verify(userRepository).save(customer);
    verify(userBranchAccessRepository).save(any(UserBranchAccess.class));

    OrganizationUser savedMembership = captureSavedMembership();
    assertEquals(UserRole.MANAGER, savedMembership.getRole());
    assertSameBranch(satna, savedMembership.getBaseBranch());
  }

  @Test
  void promoteRejectsUserWithoutOrganizationMembership() {
    mockAuthorizedContext();

    User outsider = buildCustomer(77, "Outsider");
    when(userRepository.findById(77)).thenReturn(Optional.of(outsider));
    when(organizationUserRepository.findByUserIdAndOrganizationId(outsider.getId(), organization.getId()))
        .thenReturn(Optional.empty());

    PromoteManagerRequest request = new PromoteManagerRequest();
    request.setUserId(77);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> managerAdminService.promoteManager(request, "admin@test.com"));

    assertEquals("User does not belong to this organization", exception.getMessage());
    verify(organizationUserRepository, never()).save(any(OrganizationUser.class));
  }

  @Test
  void cannotModifyAdministratorMemberships() {
    mockAuthorizedContext();

    User superAdmin = buildCustomer(30, "Owner");
    superAdmin.setRole(UserRole.SUPER_ADMIN);
    OrganizationUser superAdminMembership = buildMembership(31L, superAdmin, UserRole.SUPER_ADMIN);

    when(userRepository.findById(30)).thenReturn(Optional.of(superAdmin));
    when(organizationUserRepository.findByUserIdAndOrganizationId(superAdmin.getId(), organization.getId()))
        .thenReturn(Optional.of(superAdminMembership));

    PromoteManagerRequest request = new PromoteManagerRequest();
    request.setUserId(30);

    SecurityException exception =
        assertThrows(SecurityException.class, () -> managerAdminService.promoteManager(request, "admin@test.com"));

    assertEquals("Administrator memberships cannot be promoted", exception.getMessage());
  }

  @Test
  void demoteRevertsRoleAndGlobalRoleWhenNoOtherStaffMembershipsRemain() {
    mockAuthorizedContext();

    User manager = buildCustomer(25, "Rahul");
    manager.setRole(UserRole.MANAGER);
    OrganizationUser managerMembership = buildMembership(41L, manager, UserRole.MANAGER);

    when(organizationUserRepository.findById(41L)).thenReturn(Optional.of(managerMembership));
    when(organizationUserRepository.save(any(OrganizationUser.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(organizationUserRepository.countOtherActiveMembershipsByRoles(25, 41L, staffRoles()))
        .thenReturn(0L);

    ManagerAdminDto result = managerAdminService.demoteManager(41L, "admin@test.com");

    assertEquals(UserRole.CUSTOMER.name(), result.getRole());
    verify(userRepository).save(manager);
  }

  @Test
  void demoteKeepsGlobalRoleWhenOtherStaffMembershipsRemain() {
    mockAuthorizedContext();

    User manager = buildCustomer(25, "Rahul");
    manager.setRole(UserRole.MANAGER);
    OrganizationUser managerMembership = buildMembership(41L, manager, UserRole.MANAGER);

    when(organizationUserRepository.findById(41L)).thenReturn(Optional.of(managerMembership));
    when(organizationUserRepository.save(any(OrganizationUser.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(organizationUserRepository.countOtherActiveMembershipsByRoles(25, 41L, staffRoles()))
        .thenReturn(1L);

    managerAdminService.demoteManager(41L, "admin@test.com");

    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  void deactivateRequiresManagerRoleAndClearsActiveFlag() {
    mockAuthorizedContext();

    User manager = buildCustomer(25, "Rahul");
    manager.setRole(UserRole.MANAGER);
    OrganizationUser managerMembership = buildMembership(41L, manager, UserRole.MANAGER);

    when(organizationUserRepository.findById(41L)).thenReturn(Optional.of(managerMembership));
    when(organizationUserRepository.save(any(OrganizationUser.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(organizationUserRepository.countOtherActiveMembershipsByRoles(25, 41L, staffRoles()))
        .thenReturn(0L);

    managerAdminService.deactivateManager(41L, "admin@test.com");

    OrganizationUser saved = captureSavedMembership();
    assertFalse(saved.getIsActive());
  }

  @Test
  void branchAccessGrantValidatesCallerAccessToTargetBranch() {
    mockAuthorizedContext();

    User manager = buildCustomer(25, "Rahul");
    OrganizationUser managerMembership = buildMembership(41L, manager, UserRole.MANAGER);
    when(organizationUserRepository.findById(41L)).thenReturn(Optional.of(managerMembership));
    when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(rewa.getId(), organization.getId()))
        .thenReturn(Optional.of(rewa));
    when(userBranchAccessRepository.existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(
            adminMembership.getId(), rewa.getId()))
        .thenReturn(false);

    BranchAccessUpdateRequest request = new BranchAccessUpdateRequest();
    request.setBranchId(rewa.getId());
    request.setGranted(true);

    SecurityException exception =
        assertThrows(
            SecurityException.class,
            () -> managerAdminService.setStaffBranchAccess(41L, request, "admin@test.com"));

    assertEquals("You do not have access to manage this branch", exception.getMessage());
    verify(userBranchAccessRepository, never()).save(any(UserBranchAccess.class));
  }

  @Test
  void revokingBaseBranchIsRejected() {
    mockAuthorizedContext();

    User manager = buildCustomer(25, "Rahul");
    OrganizationUser managerMembership = buildMembership(41L, manager, UserRole.MANAGER);
    when(organizationUserRepository.findById(41L)).thenReturn(Optional.of(managerMembership));
    when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(satna.getId(), organization.getId()))
        .thenReturn(Optional.of(satna));

    BranchAccessUpdateRequest request = new BranchAccessUpdateRequest();
    request.setBranchId(satna.getId());
    request.setGranted(false);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> managerAdminService.setStaffBranchAccess(41L, request, "admin@test.com"));

    assertEquals("Cannot revoke the base branch", exception.getMessage());
  }

  @Test
  void managerListOnlyIncludesManagersWithCurrentBranchAccess() {
    mockAuthorizedContext();

    User withAccess = buildCustomer(25, "Rahul");
    OrganizationUser withAccessMembership = buildMembership(41L, withAccess, UserRole.MANAGER);

    User otherBranchManager = buildCustomer(26, "Sneha");
    OrganizationUser otherBranchMembership = buildMembership(42L, otherBranchManager, UserRole.MANAGER);
    otherBranchMembership.setBaseBranch(rewa);

    when(organizationUserRepository.findByOrganization_IdAndRoleAndIsActiveTrue(organization.getId(), UserRole.MANAGER))
        .thenReturn(List.of(withAccessMembership, otherBranchMembership));
    when(userBranchAccessRepository.findByOrganizationUserIdAndIsActiveTrue(41L)).thenReturn(List.of());

    List<ManagerAdminDto> managers = managerAdminService.getCurrentBranchManagers("admin@test.com");

    assertEquals(1, managers.size());
    assertEquals("Rahul", managers.get(0).getName());
    assertEquals(Long.valueOf(41L), managers.get(0).getOrganizationUserId());
  }

  @Test
  void nonAdminCallerIsRejected() {
    OrganizationContextDto context = new OrganizationContextDto();
    context.setCurrentRole(UserRole.MANAGER.name());
    context.setCurrentOrganization(new OrganizationOptionDto(organization.getId(), organization.getName()));
    context.setCurrentBranch(new BranchOptionDto(satna.getId(), satna.getName()));

    when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
    when(organizationContextService.resolveContext("admin@test.com")).thenReturn(context);

    SecurityException exception =
        assertThrows(SecurityException.class, () -> managerAdminService.getCurrentBranchManagers("admin@test.com"));

    assertEquals("Only admins can manage staff", exception.getMessage());
  }

  private java.util.List<UserRole> staffRoles() {
    return java.util.List.of(UserRole.MANAGER, UserRole.ADMIN, UserRole.SUPER_ADMIN);
  }

  private void mockAuthorizedContext() {
    OrganizationContextDto context = new OrganizationContextDto();
    context.setCurrentRole(UserRole.ADMIN.name());
    context.setCurrentOrganization(new OrganizationOptionDto(organization.getId(), organization.getName()));
    context.setCurrentBranch(new BranchOptionDto(satna.getId(), satna.getName()));
    context.setHasPersistedContext(true);

    when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
    when(organizationContextService.resolveContext("admin@test.com")).thenReturn(context);
    when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(admin.getId(), organization.getId()))
        .thenReturn(Optional.of(adminMembership));
    when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(satna.getId(), organization.getId()))
        .thenReturn(Optional.of(satna));
  }

  private User buildCustomer(Integer id, String name) {
    User user = new User();
    user.setId(id);
    user.setName(name);
    user.setEmail(name.toLowerCase() + "@test.com");
    user.setRole(UserRole.CUSTOMER);
    user.setIsActive(true);
    return user;
  }

  private OrganizationUser buildMembership(Long id, User user, UserRole role) {
    OrganizationUser membership = new OrganizationUser();
    membership.setId(id);
    membership.setOrganization(organization);
    membership.setUser(user);
    membership.setRole(role);
    membership.setBaseBranch(satna);
    membership.setIsActive(true);
    return membership;
  }

  private OrganizationUser captureSavedMembership() {
    org.mockito.ArgumentCaptor<OrganizationUser> captor =
        org.mockito.ArgumentCaptor.forClass(OrganizationUser.class);
    verify(organizationUserRepository).save(captor.capture());
    return captor.getValue();
  }

  private void assertSameBranch(Branch expected, Branch actual) {
    assertEquals(expected.getId(), actual == null ? null : actual.getId());
  }
}
