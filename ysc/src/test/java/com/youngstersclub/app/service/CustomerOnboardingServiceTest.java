package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.BranchOptionDto;
import com.youngstersclub.app.dto.CustomerOnboardingRequest;
import com.youngstersclub.app.dto.CustomerOnboardingResponseDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.entity.UserBranchAccess;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.OrganizationRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
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
class CustomerOnboardingServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private OrganizationRepository organizationRepository;
  @Mock private BranchRepository branchRepository;
  @Mock private OrganizationUserRepository organizationUserRepository;
  @Mock private UserBranchAccessRepository userBranchAccessRepository;
  @Mock private OrganizationContextService organizationContextService;

  @InjectMocks private CustomerOnboardingService customerOnboardingService;

  private User actor;
  private User targetCustomer;
  private Organization organization;
  private Branch satnaBranch;
  private Branch rewaBranch;

  @BeforeEach
  void setUp() {
    actor = new User();
    actor.setId(1);
    actor.setEmail("manager@test.com");
    actor.setIsActive(true);
    actor.setRole(UserRole.MANAGER);

    targetCustomer = new User();
    targetCustomer.setId(15);
    targetCustomer.setName("Pragyesh Yadav");
    targetCustomer.setEmail("pragyesh@gmail.com");
    targetCustomer.setPhone("9876543210");
    targetCustomer.setIsActive(true);
    targetCustomer.setRole(UserRole.CUSTOMER);

    organization = new Organization();
    organization.setId(100L);
    organization.setName("Youngsters Sports Club & Kids Ocean Dreamland");
    organization.setIsActive(true);

    satnaBranch = new Branch();
    satnaBranch.setId(1000L);
    satnaBranch.setName("Satna");
    satnaBranch.setOrganization(organization);
    satnaBranch.setIsActive(true);

    rewaBranch = new Branch();
    rewaBranch.setId(1001L);
    rewaBranch.setName("Rewa");
    rewaBranch.setOrganization(organization);
    rewaBranch.setIsActive(true);
  }

  @Test
  void managerCanOnboardCustomerIntoPermittedBranch() {
    actor.setRole(UserRole.MANAGER);
    OrganizationUser actorMembership = buildMembership(201L, actor, organization, satnaBranch, UserRole.MANAGER, true);

    when(userRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(actor));
    when(organizationContextService.resolveContext("manager@test.com"))
        .thenReturn(buildContext(UserRole.MANAGER, organization, satnaBranch));
    when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
        .thenReturn(Optional.of(actorMembership));
    when(userRepository.findById(targetCustomer.getId())).thenReturn(Optional.of(targetCustomer));
    when(organizationRepository.findByIdAndIsActiveTrue(organization.getId())).thenReturn(Optional.of(organization));
    when(branchRepository.findByOrganizationIdAndIdInAndIsActiveTrue(organization.getId(), List.of(satnaBranch.getId())))
        .thenReturn(List.of(satnaBranch));
    when(userBranchAccessRepository.findByOrganizationUserIdAndIsActiveTrue(actorMembership.getId()))
        .thenReturn(List.of(buildBranchAccess(301L, actorMembership, satnaBranch, true)));
    when(organizationUserRepository.findByUserIdAndOrganizationId(targetCustomer.getId(), organization.getId()))
        .thenReturn(Optional.empty());
    when(organizationUserRepository.save(any(OrganizationUser.class)))
        .thenAnswer(invocation -> {
          OrganizationUser membership = invocation.getArgument(0);
          membership.setId(501L);
          return membership;
        });
    when(userBranchAccessRepository.findByOrganizationUserId(501L)).thenReturn(List.of());

    CustomerOnboardingResponseDto response =
        customerOnboardingService.onboardCustomer(buildRequest(List.of(satnaBranch.getId()), satnaBranch.getId(), organization.getId()));

    assertTrue(response.isMembershipCreated());
    assertFalse(response.isMembershipReactivated());
    assertEquals("Satna", response.getBaseBranchName());
    assertEquals(1, response.getBranchesAdded().size());
    assertEquals("Satna", response.getBranchesAdded().get(0).getName());

    verify(userBranchAccessRepository, times(1)).save(any(UserBranchAccess.class));
  }

  @Test
  void managerCannotAssignUnauthorizedBranch() {
    actor.setRole(UserRole.MANAGER);
    OrganizationUser actorMembership = buildMembership(201L, actor, organization, satnaBranch, UserRole.MANAGER, true);

    when(userRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(actor));
    when(organizationContextService.resolveContext("manager@test.com"))
        .thenReturn(buildContext(UserRole.MANAGER, organization, satnaBranch));
    when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
        .thenReturn(Optional.of(actorMembership));
    when(userRepository.findById(targetCustomer.getId())).thenReturn(Optional.of(targetCustomer));
    when(organizationRepository.findByIdAndIsActiveTrue(organization.getId())).thenReturn(Optional.of(organization));
    when(branchRepository.findByOrganizationIdAndIdInAndIsActiveTrue(organization.getId(), List.of(rewaBranch.getId())))
        .thenReturn(List.of(rewaBranch));
    when(userBranchAccessRepository.findByOrganizationUserIdAndIsActiveTrue(actorMembership.getId()))
        .thenReturn(List.of(buildBranchAccess(301L, actorMembership, satnaBranch, true)));

    SecurityException exception = assertThrows(
        SecurityException.class,
        () -> customerOnboardingService.onboardCustomer(
            buildRequest(List.of(rewaBranch.getId()), rewaBranch.getId(), organization.getId())));

    assertEquals("You are not authorized to assign one or more selected branches", exception.getMessage());
  }

  @Test
  void existingActiveMembershipIsReusedAndOnlyMissingBranchAccessIsAdded() {
    actor.setRole(UserRole.ADMIN);
    OrganizationUser actorMembership = buildMembership(202L, actor, organization, satnaBranch, UserRole.ADMIN, true);
    OrganizationUser targetMembership =
        buildMembership(701L, targetCustomer, organization, satnaBranch, UserRole.CUSTOMER, true);

    when(userRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(actor));
    when(organizationContextService.resolveContext("manager@test.com"))
        .thenReturn(buildContext(UserRole.ADMIN, organization, satnaBranch));
    when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
        .thenReturn(Optional.of(actorMembership));
    when(userRepository.findById(targetCustomer.getId())).thenReturn(Optional.of(targetCustomer));
    when(organizationRepository.findByIdAndIsActiveTrue(organization.getId())).thenReturn(Optional.of(organization));
    when(branchRepository.findByOrganizationIdAndIdInAndIsActiveTrue(
        organization.getId(), List.of(satnaBranch.getId(), rewaBranch.getId())))
        .thenReturn(List.of(satnaBranch, rewaBranch));
    when(organizationUserRepository.findByUserIdAndOrganizationId(targetCustomer.getId(), organization.getId()))
        .thenReturn(Optional.of(targetMembership));
    when(userBranchAccessRepository.findByOrganizationUserId(targetMembership.getId()))
        .thenReturn(List.of(buildBranchAccess(401L, targetMembership, satnaBranch, true)));

    CustomerOnboardingResponseDto response =
        customerOnboardingService.onboardCustomer(
            buildRequest(List.of(satnaBranch.getId(), rewaBranch.getId()), null, organization.getId()));

    assertFalse(response.isMembershipCreated());
    assertFalse(response.isMembershipReactivated());
    assertEquals(1, response.getBranchesAdded().size());
    assertEquals("Rewa", response.getBranchesAdded().get(0).getName());
    assertEquals(1, response.getAlreadyAccessibleBranches().size());
    assertEquals("Satna", response.getAlreadyAccessibleBranches().get(0).getName());
  }

  @Test
  void baseBranchIsRequiredForFirstTimeMembership() {
    actor.setRole(UserRole.ADMIN);
    OrganizationUser actorMembership = buildMembership(202L, actor, organization, satnaBranch, UserRole.ADMIN, true);

    when(userRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(actor));
    when(organizationContextService.resolveContext("manager@test.com"))
        .thenReturn(buildContext(UserRole.ADMIN, organization, satnaBranch));
    when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
        .thenReturn(Optional.of(actorMembership));
    when(userRepository.findById(targetCustomer.getId())).thenReturn(Optional.of(targetCustomer));
    when(organizationRepository.findByIdAndIsActiveTrue(organization.getId())).thenReturn(Optional.of(organization));
    when(branchRepository.findByOrganizationIdAndIdInAndIsActiveTrue(organization.getId(), List.of(rewaBranch.getId())))
        .thenReturn(List.of(rewaBranch));
    when(organizationUserRepository.findByUserIdAndOrganizationId(targetCustomer.getId(), organization.getId()))
        .thenReturn(Optional.empty());

    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> customerOnboardingService.onboardCustomer(
            buildRequest(List.of(rewaBranch.getId()), null, organization.getId())));

    assertEquals("Base branch is required for first-time onboarding", exception.getMessage());
  }

  @Test
  void inactiveMembershipIsReactivatedAndExistingBaseBranchIsPreserved() {
    actor.setRole(UserRole.ADMIN);
    OrganizationUser actorMembership = buildMembership(202L, actor, organization, satnaBranch, UserRole.ADMIN, true);
    OrganizationUser targetMembership =
        buildMembership(702L, targetCustomer, organization, satnaBranch, UserRole.CUSTOMER, false);

    when(userRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(actor));
    when(organizationContextService.resolveContext("manager@test.com"))
        .thenReturn(buildContext(UserRole.ADMIN, organization, satnaBranch));
    when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
        .thenReturn(Optional.of(actorMembership));
    when(userRepository.findById(targetCustomer.getId())).thenReturn(Optional.of(targetCustomer));
    when(organizationRepository.findByIdAndIsActiveTrue(organization.getId())).thenReturn(Optional.of(organization));
    when(branchRepository.findByOrganizationIdAndIdInAndIsActiveTrue(organization.getId(), List.of(satnaBranch.getId())))
        .thenReturn(List.of(satnaBranch));
    when(organizationUserRepository.findByUserIdAndOrganizationId(targetCustomer.getId(), organization.getId()))
        .thenReturn(Optional.of(targetMembership));
    when(organizationUserRepository.save(any(OrganizationUser.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(userBranchAccessRepository.findByOrganizationUserId(targetMembership.getId())).thenReturn(List.of());

    CustomerOnboardingResponseDto response =
        customerOnboardingService.onboardCustomer(
            buildRequest(List.of(satnaBranch.getId()), null, organization.getId()));

    assertTrue(response.isMembershipReactivated());
    assertEquals("Satna", response.getBaseBranchName());

    ArgumentCaptor<OrganizationUser> membershipCaptor = ArgumentCaptor.forClass(OrganizationUser.class);
    verify(organizationUserRepository).save(membershipCaptor.capture());
    assertTrue(Boolean.TRUE.equals(membershipCaptor.getValue().getIsActive()));
  }

  private CustomerOnboardingRequest buildRequest(List<Long> branchIds, Long baseBranchId, Long organizationId) {
    CustomerOnboardingRequest request = new CustomerOnboardingRequest();
    request.setActorEmail("manager@test.com");
    request.setUserId(targetCustomer.getId());
    request.setOrganizationId(organizationId);
    request.setBranchIds(branchIds);
    request.setBaseBranchId(baseBranchId);
    return request;
  }

  private OrganizationContextDto buildContext(UserRole role, Organization org, Branch branch) {
    OrganizationContextDto context = new OrganizationContextDto();
    context.setUserId(actor.getId());
    context.setCurrentRole(role.name());
    context.setCurrentOrganization(new OrganizationOptionDto(org.getId(), org.getName()));
    context.setCurrentBranch(new BranchOptionDto(branch.getId(), branch.getName()));
    context.setAvailableOrganizations(List.of(new OrganizationOptionDto(org.getId(), org.getName())));
    context.setAccessibleBranches(List.of(new BranchOptionDto(branch.getId(), branch.getName())));
    context.setHasPersistedContext(true);
    context.setRequiresSelection(false);
    return context;
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
    membership.setCreatedAt(LocalDateTime.now());
    return membership;
  }

  private UserBranchAccess buildBranchAccess(
      Long id,
      OrganizationUser organizationUser,
      Branch branch,
      boolean active) {
    UserBranchAccess access = new UserBranchAccess();
    access.setId(id);
    access.setOrganizationUser(organizationUser);
    access.setBranch(branch);
    access.setIsActive(active);
    access.setGrantedAt(LocalDateTime.now());
    access.setCreatedAt(LocalDateTime.now());
    return access;
  }
}
