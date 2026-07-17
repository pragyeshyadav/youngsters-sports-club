package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.BranchOptionDto;
import com.youngstersclub.app.dto.CreateCustomerRequest;
import com.youngstersclub.app.dto.CreateCustomerResponseDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
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
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceCreateCustomerTest {

  @Mock private UserRepository userRepository;
  @Mock private OrganizationContextService organizationContextService;
  @Mock private OrganizationUserRepository organizationUserRepository;
  @Mock private UserBranchAccessRepository userBranchAccessRepository;
  @Mock private BranchRepository branchRepository;

  @InjectMocks private UserService userService;

  private User actor;
  private Organization organization;
  private Branch branch;
  private OrganizationUser actorMembership;

  @BeforeEach
  void setUp() {
    actor = new User();
    actor.setId(1);
    actor.setEmail("manager@test.com");
    actor.setRole(UserRole.MANAGER);
    actor.setIsActive(true);

    organization = new Organization();
    organization.setId(10L);
    organization.setName("Youngsters Sports Club & Kids Ocean Dreamland");
    organization.setIsActive(true);

    branch = new Branch();
    branch.setId(20L);
    branch.setName("Satna");
    branch.setOrganization(organization);
    branch.setIsActive(true);

    actorMembership = new OrganizationUser();
    actorMembership.setId(30L);
    actorMembership.setOrganization(organization);
    actorMembership.setUser(actor);
    actorMembership.setRole(UserRole.MANAGER);
    actorMembership.setBaseBranch(branch);
    actorMembership.setIsActive(true);
    actorMembership.setCreatedAt(LocalDateTime.now());
  }

  @Test
  void createsManualCustomerInCurrentContext() {
    when(userRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(actor));
    when(organizationContextService.resolveContext("manager@test.com")).thenReturn(buildContext());
    when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
        .thenReturn(Optional.of(actorMembership));
    when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branch.getId(), organization.getId()))
        .thenReturn(Optional.of(branch));
    when(userRepository.findByEmail("dummy_rahul_sharma_9876543210@gmail.com")).thenReturn(Optional.empty());
    when(userRepository.findByPhone("9876543210")).thenReturn(Optional.empty());
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
      User user = invocation.getArgument(0);
      user.setId(99);
      return user;
    });
    when(organizationUserRepository.findByUserIdAndOrganizationId(99, organization.getId()))
        .thenReturn(Optional.empty());
    when(organizationUserRepository.save(any(OrganizationUser.class))).thenAnswer(invocation -> {
      OrganizationUser membership = invocation.getArgument(0);
      membership.setId(40L);
      return membership;
    });
    when(userBranchAccessRepository.findByOrganizationUserIdAndBranchId(40L, branch.getId())).thenReturn(Optional.empty());

    CreateCustomerResponseDto response =
        userService.createManualCustomerInCurrentContext(buildRequest("Rahul Sharma", "", "9876543210"), "manager@test.com");

    assertEquals("Customer created successfully", response.getMessage());
    assertEquals(99, response.getUserId());
    assertEquals("Satna", response.getBaseBranchName());
    assertTrue(response.isMembershipCreated());
    assertTrue(response.isBranchAccessCreated());
  }

  @Test
  void reusesInactiveMembershipAndBranchAccess() {
    OrganizationUser customerMembership = new OrganizationUser();
    customerMembership.setId(55L);
    customerMembership.setOrganization(organization);
    User savedCustomer = new User();
    savedCustomer.setId(100);
    savedCustomer.setName("Rahul Sharma");
    savedCustomer.setEmail("rahul@example.com");
    savedCustomer.setGoogleId("MANUAL_USER_9876543210");
    savedCustomer.setPhone("9876543210");
    savedCustomer.setRole(UserRole.CUSTOMER);
    savedCustomer.setIsActive(true);
    customerMembership.setUser(savedCustomer);
    customerMembership.setRole(UserRole.CUSTOMER);
    customerMembership.setBaseBranch(branch);
    customerMembership.setIsActive(false);

    UserBranchAccess branchAccess = new UserBranchAccess();
    branchAccess.setId(56L);
    branchAccess.setOrganizationUser(customerMembership);
    branchAccess.setBranch(branch);
    branchAccess.setIsActive(false);

    when(userRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(actor));
    when(organizationContextService.resolveContext("manager@test.com")).thenReturn(buildContext());
    when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
        .thenReturn(Optional.of(actorMembership));
    when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branch.getId(), organization.getId()))
        .thenReturn(Optional.of(branch));
    when(userRepository.findByEmail("rahul@example.com")).thenReturn(Optional.empty());
    when(userRepository.findByPhone("9876543210")).thenReturn(Optional.empty());
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
      User user = invocation.getArgument(0);
      user.setId(100);
      return user;
    });
    when(organizationUserRepository.findByUserIdAndOrganizationId(100, organization.getId()))
        .thenReturn(Optional.of(customerMembership));
    when(organizationUserRepository.save(any(OrganizationUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(userBranchAccessRepository.findByOrganizationUserIdAndBranchId(customerMembership.getId(), branch.getId()))
        .thenReturn(Optional.of(branchAccess));

    CreateCustomerResponseDto response =
        userService.createManualCustomerInCurrentContext(buildRequest("Rahul Sharma", "rahul@example.com", "9876543210"), "manager@test.com");

    assertFalse(response.isMembershipCreated());
    assertTrue(response.isMembershipReactivated());
    assertFalse(response.isBranchAccessCreated());
    assertTrue(response.isBranchAccessReactivated());
  }

  @Test
  void rejectsDuplicatePhoneUsingExistingBehavior() {
    when(userRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(actor));
    when(organizationContextService.resolveContext("manager@test.com")).thenReturn(buildContext());
    when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
        .thenReturn(Optional.of(actorMembership));
    when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branch.getId(), organization.getId()))
        .thenReturn(Optional.of(branch));
    when(userRepository.findByEmail("dummy_rahul_sharma_9876543210@gmail.com")).thenReturn(Optional.empty());
    when(userRepository.findByPhone("9876543210")).thenReturn(Optional.of(new User()));

    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> userService.createManualCustomerInCurrentContext(
            buildRequest("Rahul Sharma", "", "9876543210"),
            "manager@test.com"));

    assertEquals("Customer with this mobile number already exists", exception.getMessage());
    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  void rejectsCallerWithoutCurrentBranchAccess() {
    when(userRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(actor));
    when(organizationContextService.resolveContext("manager@test.com")).thenReturn(buildContext());
    when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
        .thenReturn(Optional.of(actorMembership));
    when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branch.getId(), organization.getId()))
        .thenReturn(Optional.of(branch));
    actorMembership.setBaseBranch(null);
    when(userBranchAccessRepository.existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(actorMembership.getId(), branch.getId()))
        .thenReturn(false);

    SecurityException exception = assertThrows(
        SecurityException.class,
        () -> userService.createManualCustomerInCurrentContext(
            buildRequest("Rahul Sharma", "", "9876543210"),
            "manager@test.com"));

    assertEquals("You do not have access to the current branch", exception.getMessage());
  }

  private CreateCustomerRequest buildRequest(String name, String email, String mobileNumber) {
    CreateCustomerRequest request = new CreateCustomerRequest();
    request.setName(name);
    request.setEmail(email);
    request.setMobileNumber(mobileNumber);
    return request;
  }

  private OrganizationContextDto buildContext() {
    OrganizationContextDto dto = new OrganizationContextDto();
    dto.setCurrentRole(UserRole.MANAGER.name());
    dto.setCurrentOrganization(new OrganizationOptionDto(organization.getId(), organization.getName()));
    dto.setCurrentBranch(new BranchOptionDto(branch.getId(), branch.getName()));
    dto.setHasPersistedContext(true);
    dto.setRequiresSelection(false);
    return dto;
  }
}
