package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.BranchOptionDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
import com.youngstersclub.app.dto.SnookerTableAdminRequest;
import com.youngstersclub.app.dto.SnookerTableResponseDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.SnookerTable;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.FrameRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.SnookerTableRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.math.BigDecimal;
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
class SnookerTableAdminServiceTest {

  @Mock private SnookerTableRepository snookerTableRepository;
  @Mock private FrameRepository frameRepository;
  @Mock private UserRepository userRepository;
  @Mock private OrganizationContextService organizationContextService;
  @Mock private OrganizationUserRepository organizationUserRepository;
  @Mock private BranchRepository branchRepository;
  @Mock private UserBranchAccessRepository userBranchAccessRepository;

  @InjectMocks private SnookerTableService snookerTableService;

  private User admin;
  private Organization organization;
  private Branch branch;
  private OrganizationUser membership;

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

    branch = new Branch();
    branch.setId(2L);
    branch.setName("Satna");
    branch.setOrganization(organization);
    branch.setIsActive(true);

    membership = new OrganizationUser();
    membership.setId(20L);
    membership.setOrganization(organization);
    membership.setUser(admin);
    membership.setRole(UserRole.ADMIN);
    membership.setBaseBranch(branch);
    membership.setIsActive(true);
  }

  @Test
  void adminListReturnsAllBranchTablesIncludingInactive() {
    mockAuthorizedContext();
    SnookerTable active = buildTable(101L, "Sharma S1", true, true);
    SnookerTable inactive = buildTable(102L, "Old Table", false, true);
    when(snookerTableRepository.findByBranch_IdOrderByIdAsc(branch.getId()))
        .thenReturn(List.of(active, inactive));

    List<SnookerTableResponseDto> result =
        snookerTableService.getCurrentBranchTablesForAdmin("admin@test.com");

    assertEquals(2, result.size());
    assertTrue(result.stream().anyMatch(table -> Boolean.FALSE.equals(table.getActive())));
    verify(snookerTableRepository).findByBranch_IdOrderByIdAsc(branch.getId());
  }

  @Test
  void createTableAssignsCurrentBranchAndRejectsDuplicateName() {
    mockAuthorizedContext();
    when(snookerTableRepository.findByBranch_IdAndTableNameIgnoreCase(branch.getId(), "Sharma S3"))
        .thenReturn(Optional.empty());
    when(snookerTableRepository.save(any(SnookerTable.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    SnookerTableResponseDto response =
        snookerTableService.createTable(buildRequest(" Sharma S3 ", "4.5"), "admin@test.com");

    ArgumentCaptor<SnookerTable> captor = ArgumentCaptor.forClass(SnookerTable.class);
    verify(snookerTableRepository).save(captor.capture());
    SnookerTable saved = captor.getValue();

    assertSame(branch, saved.getBranch());
    assertEquals("Sharma S3", saved.getTableName());
    assertEquals(0, BigDecimal.valueOf(4.5).compareTo(saved.getRatePerMinute()));
    assertTrue(saved.getIsActive());
    assertTrue(saved.getIsAvailable());
    assertEquals(branch.getId(), response.getBranchId());
  }

  @Test
  void duplicateTableNameWithinBranchIsRejected() {
    mockAuthorizedContext();
    when(snookerTableRepository.findByBranch_IdAndTableNameIgnoreCase(branch.getId(), "sharma s1"))
        .thenReturn(Optional.of(buildTable(101L, "Sharma S1", true, true)));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> snookerTableService.createTable(buildRequest("sharma s1", "4"), "admin@test.com"));

    assertEquals("A snooker table with this name already exists", exception.getMessage());
    verify(snookerTableRepository, never()).save(any(SnookerTable.class));
  }

  @Test
  void updateRejectsCrossBranchTableIdAsNotFound() {
    mockAuthorizedContext();
    when(snookerTableRepository.findByIdAndBranch_Id(999L, branch.getId())).thenReturn(Optional.empty());

    java.util.NoSuchElementException exception =
        assertThrows(
            java.util.NoSuchElementException.class,
            () -> snookerTableService.updateTable(999L, buildRequest("Renamed", "4"), "admin@test.com"));

    assertEquals("Snooker table not found", exception.getMessage());
  }

  @Test
  void cannotDeactivateTableWhileInUse() {
    mockAuthorizedContext();
    when(snookerTableRepository.findByIdAndBranch_Id(101L, branch.getId()))
        .thenReturn(Optional.of(buildTable(101L, "Sharma S1", true, false)));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> snookerTableService.setTableActive(101L, false, "admin@test.com"));

    assertEquals("Table is currently in use and cannot be deactivated", exception.getMessage());
    verify(snookerTableRepository, never()).save(any(SnookerTable.class));
  }

  @Test
  void releaseResetsAvailabilityForStuckTable() {
    mockAuthorizedContext();
    SnookerTable stuck = buildTable(101L, "Sharma S1", true, false);
    when(snookerTableRepository.findByIdAndBranch_Id(101L, branch.getId())).thenReturn(Optional.of(stuck));
    when(snookerTableRepository.save(stuck)).thenReturn(stuck);

    SnookerTableResponseDto response = snookerTableService.releaseTable(101L, "admin@test.com");

    assertTrue(response.getAvailable());
    verify(snookerTableRepository).save(stuck);
  }

  @Test
  void managerRoleCannotCreateTables() {
    mockUnauthorizedRoleContext();

    SecurityException exception =
        assertThrows(
            SecurityException.class,
            () -> snookerTableService.createTable(buildRequest("New Table", "4"), "manager@test.com"));

    assertEquals("Only admins can add snooker tables", exception.getMessage());
  }

  private void mockAuthorizedContext() {
    OrganizationContextDto context = new OrganizationContextDto();
    context.setCurrentRole(UserRole.ADMIN.name());
    context.setCurrentOrganization(new OrganizationOptionDto(organization.getId(), organization.getName()));
    context.setCurrentBranch(new BranchOptionDto(branch.getId(), branch.getName()));
    context.setHasPersistedContext(true);

    when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
    when(organizationContextService.resolveContext("admin@test.com")).thenReturn(context);
    when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(admin.getId(), organization.getId()))
        .thenReturn(Optional.of(membership));
    when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branch.getId(), organization.getId()))
        .thenReturn(Optional.of(branch));
  }

  private void mockUnauthorizedRoleContext() {
    User manager = new User();
    manager.setId(15);
    manager.setEmail("manager@test.com");
    manager.setRole(UserRole.MANAGER);
    manager.setIsActive(true);

    OrganizationUser managerMembership = new OrganizationUser();
    managerMembership.setId(21L);
    managerMembership.setOrganization(organization);
    managerMembership.setUser(manager);
    managerMembership.setRole(UserRole.MANAGER);
    managerMembership.setBaseBranch(branch);
    managerMembership.setIsActive(true);

    OrganizationContextDto context = new OrganizationContextDto();
    context.setCurrentRole(UserRole.MANAGER.name());
    context.setCurrentOrganization(new OrganizationOptionDto(organization.getId(), organization.getName()));
    context.setCurrentBranch(new BranchOptionDto(branch.getId(), branch.getName()));

    when(userRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(manager));
    when(organizationContextService.resolveContext("manager@test.com")).thenReturn(context);
    when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(manager.getId(), organization.getId()))
        .thenReturn(Optional.of(managerMembership));
    when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branch.getId(), organization.getId()))
        .thenReturn(Optional.of(branch));
  }

  private SnookerTableAdminRequest buildRequest(String tableName, String rate) {
    SnookerTableAdminRequest request = new SnookerTableAdminRequest();
    request.setTableName(tableName);
    request.setRatePerMinute(new BigDecimal(rate));
    return request;
  }

  private SnookerTable buildTable(Long id, String tableName, boolean isActive, boolean available) {
    SnookerTable table = new SnookerTable();
    table.setId(id);
    table.setTableName(tableName);
    table.setRatePerMinute(BigDecimal.valueOf(3.5));
    table.setIsActive(isActive);
    table.setIsAvailable(available);
    table.setBranch(branch);
    return table;
  }
}
