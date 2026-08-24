package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.OrganizationRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.SnookerTableRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
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
class OrganizationContextServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private OrganizationRepository organizationRepository;
  @Mock private BranchRepository branchRepository;
  @Mock private OrganizationUserRepository organizationUserRepository;
  @Mock private UserBranchAccessRepository userBranchAccessRepository;
  @Mock private SnookerTableRepository snookerTableRepository;

  @InjectMocks private OrganizationContextService organizationContextService;

  private User user;
  private Organization organization;
  private Branch branch;
  private OrganizationUser membership;

  @BeforeEach
  void setUp() {
    user = new User();
    user.setId(101);
    user.setEmail("customer@test.com");
    user.setRole(UserRole.CUSTOMER);
    user.setIsActive(true);

    organization = new Organization();
    organization.setId(1L);
    organization.setName("Youngsters Sports Club & Kids Ocean Dreamland");
    organization.setLogoUrl("https://example.com/org-logo.png");
    organization.setIsActive(true);

    branch = new Branch();
    branch.setId(2L);
    branch.setName("Satna");
    branch.setOrganization(organization);
    branch.setIsActive(true);

    membership = new OrganizationUser();
    membership.setId(201L);
    membership.setUser(user);
    membership.setOrganization(organization);
    membership.setRole(UserRole.CUSTOMER);
    membership.setBaseBranch(branch);
    membership.setIsActive(true);
    membership.setCreatedAt(LocalDateTime.now());
  }

  @Test
  void resolveContextMarksKidsPlayEnabledWhenCurrentBranchHasConfiguredKidsPlayTable() {
    mockContextLoad();
    when(snookerTableRepository.existsByBranch_IdAndTableNameIgnoreCaseAndIsActiveTrue(
        branch.getId(),
        OrganizationContextService.KIDS_PLAY_TABLE_NAME)).thenReturn(true);

    OrganizationContextDto context = organizationContextService.resolveContext("customer@test.com");

    assertTrue(context.isKidsPlayEnabled());
  }

  @Test
  void resolveContextMarksKidsPlayDisabledWhenCurrentBranchHasNoConfiguredKidsPlayTable() {
    mockContextLoad();
    when(snookerTableRepository.existsByBranch_IdAndTableNameIgnoreCaseAndIsActiveTrue(
        branch.getId(),
        OrganizationContextService.KIDS_PLAY_TABLE_NAME)).thenReturn(false);

    OrganizationContextDto context = organizationContextService.resolveContext("customer@test.com");

    assertFalse(context.isKidsPlayEnabled());
  }

  @Test
  void resolveContextIncludesCurrentOrganizationLogoUrl() {
    mockContextLoad();
    when(snookerTableRepository.existsByBranch_IdAndTableNameIgnoreCaseAndIsActiveTrue(
        branch.getId(),
        OrganizationContextService.KIDS_PLAY_TABLE_NAME)).thenReturn(false);

    OrganizationContextDto context = organizationContextService.resolveContext("customer@test.com");

    assertNotNull(context.getCurrentOrganization());
    assertEquals("https://example.com/org-logo.png", context.getCurrentOrganization().getLogoUrl());
  }

  private void mockContextLoad() {
    when(userRepository.findByEmail("customer@test.com")).thenReturn(Optional.of(user));
    when(organizationUserRepository.findByUserIdAndIsActiveTrue(user.getId())).thenReturn(List.of(membership));
    when(userBranchAccessRepository.findByOrganizationUserIdAndIsActiveTrue(membership.getId())).thenReturn(List.of());
  }
}
