package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.BranchOptionDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
import com.youngstersclub.app.dto.SnookerTableResponseDto;
import com.youngstersclub.app.dto.SnookerTableStatusDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.Frame;
import com.youngstersclub.app.entity.FramePlayer;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.SnookerTable;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.entity.UserBranchAccess;
import com.youngstersclub.app.enums.FrameStatus;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.FrameRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.SnookerTableRepository;
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
class SnookerTableServiceTest {

  @Mock private SnookerTableRepository snookerTableRepository;
  @Mock private FrameRepository frameRepository;
  @Mock private UserRepository userRepository;
  @Mock private OrganizationContextService organizationContextService;
  @Mock private OrganizationUserRepository organizationUserRepository;
  @Mock private BranchRepository branchRepository;
  @Mock private UserBranchAccessRepository userBranchAccessRepository;

  @InjectMocks private SnookerTableService snookerTableService;

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
    organization.setName("Youngsters Sports Club & Kids Ocean Dreamland");
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
  void currentSatnaContextReturnsOnlySatnaAvailableTables() {
    SnookerTable satnaTable = buildTable(101L, "Sharma S1", true);

    mockAuthorizedContext();
    when(snookerTableRepository.findAvailableTablesSafeByBranchId(branch.getId())).thenReturn(List.of(satnaTable));

    List<SnookerTableResponseDto> result =
        snookerTableService.getCurrentBranchAvailableTables("manager@test.com");

    assertEquals(1, result.size());
    assertEquals("Sharma S1", result.get(0).getTableName());
    assertEquals(branch.getId(), result.get(0).getBranchId());
    verify(snookerTableRepository).findAvailableTablesSafeByBranchId(branch.getId());
  }

  @Test
  void currentBranchStatusesAreBuiltFromCurrentBranchOnly() {
    SnookerTable table = buildTable(101L, "Sharma S1", false);
    Frame frame = new Frame();
    frame.setId(500);
    frame.setBranch(branch);
    frame.setSnookerTable(table);
    frame.setStatus(FrameStatus.STARTED);
    frame.setStartTime(LocalDateTime.now().minusMinutes(5));

    User playerUser = new User();
    playerUser.setId(88);
    playerUser.setName("Pragyesh Yadav");
    FramePlayer framePlayer = new FramePlayer();
    framePlayer.setId(600);
    framePlayer.setFrame(frame);
    framePlayer.setUser(playerUser);
    framePlayer.setPlayerName("Pragyesh Yadav");
    frame.setFramePlayers(List.of(framePlayer));

    mockAuthorizedContext();
    when(snookerTableRepository.findByBranch_IdAndIsActiveTrueOrderByIdAsc(branch.getId()))
        .thenReturn(List.of(table));
    when(frameRepository.findAllOngoingFramesByBranchId(branch.getId())).thenReturn(List.of(frame));

    List<SnookerTableStatusDto> result =
        snookerTableService.getCurrentBranchTableStatuses("manager@test.com");

    assertEquals(1, result.size());
    assertEquals("Sharma S1", result.get(0).getTableName());
    assertEquals(1, result.get(0).getPlayers().size());
    assertEquals("Pragyesh Yadav", result.get(0).getPlayers().get(0));
  }

  @Test
  void unauthorizedBranchContextIsRejected() {
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

    SecurityException exception =
        assertThrows(
            SecurityException.class,
            () -> snookerTableService.getCurrentBranchAvailableTables("manager@test.com"));

    assertEquals("You do not have access to the current branch", exception.getMessage());
  }

  @Test
  void crossBranchTableLookupReturnsNotFound() {
    mockAuthorizedContext();
    when(snookerTableRepository.findByIdAndBranch_Id(999L, branch.getId())).thenReturn(Optional.empty());

    java.util.NoSuchElementException exception =
        assertThrows(
            java.util.NoSuchElementException.class,
            () -> snookerTableService.requireCurrentBranchTable(999L, "manager@test.com"));

    assertEquals("Snooker table not found", exception.getMessage());
  }

  private void mockAuthorizedContext() {
    when(userRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(actor));
    when(organizationContextService.resolveContext("manager@test.com")).thenReturn(buildContext());
    when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
        .thenReturn(Optional.of(membership));
    when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branch.getId(), organization.getId()))
        .thenReturn(Optional.of(branch));
  }

  private OrganizationContextDto buildContext() {
    OrganizationContextDto context = new OrganizationContextDto();
    context.setCurrentRole(UserRole.MANAGER.name());
    context.setCurrentOrganization(new OrganizationOptionDto(organization.getId(), organization.getName()));
    context.setCurrentBranch(new BranchOptionDto(branch.getId(), branch.getName()));
    context.setHasPersistedContext(true);
    context.setRequiresSelection(false);
    return context;
  }

  private SnookerTable buildTable(Long id, String tableName, boolean available) {
    SnookerTable table = new SnookerTable();
    table.setId(id);
    table.setTableName(tableName);
    table.setRatePerMinute(BigDecimal.valueOf(3.5));
    table.setIsActive(true);
    table.setIsAvailable(available);
    table.setBranch(branch);
    return table;
  }
}
