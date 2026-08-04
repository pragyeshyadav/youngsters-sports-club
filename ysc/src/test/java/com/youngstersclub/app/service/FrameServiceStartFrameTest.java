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
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
import com.youngstersclub.app.dto.StartFrameRequest;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.Frame;
import com.youngstersclub.app.entity.FramePlayer;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.SnookerTable;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.FrameStatus;
import com.youngstersclub.app.enums.PaymentStatus;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.FramePlayerRepository;
import com.youngstersclub.app.repository.FrameRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.SnookerTableRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FrameServiceStartFrameTest {

  @Mock private SnookerTableRepository tableRepository;
  @Mock private FrameRepository frameRepository;
  @Mock private FramePlayerRepository framePlayerRepository;
  @Mock private UserRepository userRepository;
  @Mock private OrganizationContextService organizationContextService;
  @Mock private OrganizationUserRepository organizationUserRepository;
  @Mock private BranchRepository branchRepository;
  @Mock private UserBranchAccessRepository userBranchAccessRepository;
  @Mock private UserDueService userDueService;
  @Mock private LeaderboardCacheService leaderboardCacheService;

  @InjectMocks private FrameService frameService;

  private User actor;
  private Organization organization;
  private Branch branch;
  private Branch otherBranch;
  private OrganizationUser membership;
  private SnookerTable table;

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

    otherBranch = new Branch();
    otherBranch.setId(3L);
    otherBranch.setName("Rewa");
    otherBranch.setOrganization(organization);
    otherBranch.setIsActive(true);

    membership = new OrganizationUser();
    membership.setId(20L);
    membership.setOrganization(organization);
    membership.setUser(actor);
    membership.setRole(UserRole.MANAGER);
    membership.setBaseBranch(branch);
    membership.setIsActive(true);
    membership.setCreatedAt(LocalDateTime.now());

    table = new SnookerTable();
    table.setId(101L);
    table.setTableName("Sharma S1");
    table.setRatePerMinute(BigDecimal.valueOf(3.5));
    table.setIsActive(true);
    table.setIsAvailable(true);
    table.setBranch(branch);
  }

  @Test
  void startFramePersistsCurrentBranchAndPlayers() {
    mockAuthorizedContext();
    when(tableRepository.findAvailableTablesSafeByBranchId(branch.getId())).thenReturn(List.of(table));
    when(tableRepository.findByIdAndBranch_IdAndIsActiveTrue(table.getId(), branch.getId()))
        .thenReturn(Optional.of(table));
    when(tableRepository.save(any(SnookerTable.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(frameRepository.save(any(Frame.class))).thenAnswer(invocation -> {
      Frame frame = invocation.getArgument(0);
      frame.setId(501);
      return frame;
    });

    User playerOne = buildPlayer(91, "Player One");
    User playerTwo = buildPlayer(92, "Player Two");
    when(userRepository.findById(91)).thenReturn(Optional.of(playerOne));
    when(userRepository.findById(92)).thenReturn(Optional.of(playerTwo));

    Integer frameId = frameService.startFrame(buildStartFrameRequest(table.getId()), "manager@test.com");

    assertEquals(501, frameId);

    ArgumentCaptor<Frame> frameCaptor = ArgumentCaptor.forClass(Frame.class);
    verify(frameRepository).save(frameCaptor.capture());
    Frame savedFrame = frameCaptor.getValue();
    assertNotNull(savedFrame.getBranch());
    assertEquals(branch.getId(), savedFrame.getBranch().getId());
    assertEquals(table.getId(), savedFrame.getSnookerTable().getId());
    assertEquals(actor.getId(), savedFrame.getStartedBy().getId());
    assertEquals(FrameStatus.STARTED, savedFrame.getStatus());
    assertEquals(PaymentStatus.UNPAID, savedFrame.getPaymentStatus());
  }

  @Test
  void startFrameRejectsCrossBranchTableLookup() {
    mockAuthorizedContext();
    when(tableRepository.findAvailableTablesSafeByBranchId(branch.getId())).thenReturn(List.of(table));
    when(tableRepository.findByIdAndBranch_IdAndIsActiveTrue(999L, branch.getId()))
        .thenReturn(Optional.empty());

    NoSuchElementException exception = assertThrows(
        NoSuchElementException.class,
        () -> frameService.startFrame(buildStartFrameRequest(999L), "manager@test.com"));

    assertEquals("Table not found", exception.getMessage());
    verify(frameRepository, never()).save(any(Frame.class));
  }

  @Test
  void startFrameKeepsExistingOccupiedTableValidation() {
    mockAuthorizedContext();
    table.setIsAvailable(false);
    when(tableRepository.findAvailableTablesSafeByBranchId(branch.getId())).thenReturn(List.of(table));
    when(tableRepository.findByIdAndBranch_IdAndIsActiveTrue(table.getId(), branch.getId()))
        .thenReturn(Optional.of(table));

    RuntimeException exception = assertThrows(
        RuntimeException.class,
        () -> frameService.startFrame(buildStartFrameRequest(table.getId()), "manager@test.com"));

    assertEquals("Table is not available", exception.getMessage());
    verify(frameRepository, never()).save(any(Frame.class));
  }

  @Test
  void endFrameUsesHistoricalBranchForDueGeneration() {
    mockAuthorizedContext();
    Frame frame = buildStartedFrame(700, branch, table);
    FramePlayer winnerPlayer = buildFramePlayer(frame, buildPlayer(91, "Winner"), true, false, BigDecimal.ZERO, PaymentStatus.PAID);
    FramePlayer loserPlayer = buildFramePlayer(frame, buildPlayer(92, "Loser"), false, true, BigDecimal.ZERO, PaymentStatus.UNPAID);

    when(frameRepository.findForUpdateByIdAndBranchId(frame.getId(), branch.getId())).thenReturn(Optional.of(frame));
    when(framePlayerRepository.findByFrame_Id(frame.getId())).thenReturn(List.of(winnerPlayer, loserPlayer));
    when(userRepository.findById(91)).thenReturn(Optional.of(winnerPlayer.getUser()));
    when(userRepository.findById(92)).thenReturn(Optional.of(loserPlayer.getUser()));
    when(frameRepository.save(any(Frame.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(tableRepository.save(any(SnookerTable.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(framePlayerRepository.save(any(FramePlayer.class))).thenAnswer(invocation -> invocation.getArgument(0));
    frameService.endFrame(frame.getId(), buildSingleEndRequest(91, 92), "manager@test.com");

    verify(userDueService).syncBranchDue(loserPlayer.getUser(), branch);
    assertEquals(branch.getId(), frame.getBranch().getId());
    assertEquals(FrameStatus.ENDED, frame.getStatus());
    assertEquals(true, table.getIsAvailable());
  }

  @Test
  void endFrameRejectsCrossBranchFrame() {
    mockAuthorizedContext();
    when(frameRepository.findForUpdateByIdAndBranchId(800, branch.getId())).thenReturn(Optional.empty());

    NoSuchElementException exception = assertThrows(
        NoSuchElementException.class,
        () -> frameService.endFrame(800, buildSingleEndRequest(91, 92), "manager@test.com"));

    assertEquals("Frame not found", exception.getMessage());
    verify(userDueService, never()).syncBranchDue(any(User.class), any(Branch.class));
  }

  @Test
  void endFrameDoesNotTouchDueFromAnotherBranch() {
    mockAuthorizedContext();
    Frame frame = buildStartedFrame(900, branch, table);
    FramePlayer winnerPlayer = buildFramePlayer(frame, buildPlayer(91, "Winner"), true, false, BigDecimal.ZERO, PaymentStatus.PAID);
    FramePlayer loserPlayer = buildFramePlayer(frame, buildPlayer(92, "Loser"), false, true, BigDecimal.ZERO, PaymentStatus.UNPAID);
    when(frameRepository.findForUpdateByIdAndBranchId(frame.getId(), branch.getId())).thenReturn(Optional.of(frame));
    when(framePlayerRepository.findByFrame_Id(frame.getId())).thenReturn(List.of(winnerPlayer, loserPlayer));
    when(userRepository.findById(91)).thenReturn(Optional.of(winnerPlayer.getUser()));
    when(userRepository.findById(92)).thenReturn(Optional.of(loserPlayer.getUser()));
    when(frameRepository.save(any(Frame.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(tableRepository.save(any(SnookerTable.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(framePlayerRepository.save(any(FramePlayer.class))).thenAnswer(invocation -> invocation.getArgument(0));

    frameService.endFrame(frame.getId(), buildSingleEndRequest(91, 92), "manager@test.com");

    verify(userDueService, never()).syncBranchDue(loserPlayer.getUser(), otherBranch);
  }

  @Test
  void getTodayOngoingFramesReturnsOnlyCurrentBranchFrames() {
    mockAuthorizedContext();
    Frame frame = buildStartedFrame(1001, branch, table);
    FramePlayer framePlayer = buildFramePlayer(frame, buildPlayer(91, "Player One"), false, false, BigDecimal.ZERO, PaymentStatus.PAID);
    frame.setFramePlayers(List.of(framePlayer));

    when(frameRepository.findTodayOngoingFramesByBranchId(any(Long.class), any(LocalDateTime.class), any(LocalDateTime.class)))
        .thenReturn(List.of(frame));

    List<Map<String, Object>> result = frameService.getTodayOngoingFrames("manager@test.com");

    assertEquals(1, result.size());
    assertEquals(frame.getId(), result.get(0).get("id"));
    verify(frameRepository).findTodayOngoingFramesByBranchId(any(Long.class), any(LocalDateTime.class), any(LocalDateTime.class));
  }

  @Test
  void getCompletedFramesByDateReturnsOnlyCurrentBranchFrames() {
    mockAuthorizedContext();
    Frame completedFrame = buildStartedFrame(1002, branch, table);
    completedFrame.setStatus(FrameStatus.ENDED);
    completedFrame.setEndTime(LocalDateTime.now());
    completedFrame.setTotalAmount(BigDecimal.TEN);
    completedFrame.setPaymentDue(BigDecimal.TEN);
    completedFrame.setWinner(buildPlayer(91, "Winner"));
    completedFrame.setLooser(buildPlayer(92, "Loser"));

    when(frameRepository.findTodayCompletedFramesByBranchId(any(Long.class), any(LocalDateTime.class), any(LocalDateTime.class)))
        .thenReturn(List.of(completedFrame));

    List<Map<String, Object>> result = frameService.getCompletedFramesByDate(LocalDate.now(), "manager@test.com");

    assertEquals(1, result.size());
    assertEquals(completedFrame.getId(), result.get(0).get("id"));
    verify(frameRepository).findTodayCompletedFramesByBranchId(any(Long.class), any(LocalDateTime.class), any(LocalDateTime.class));
  }

  @Test
  void getFrameDetailsRejectsCrossBranchFrameRead() {
    mockAuthorizedContext();
    when(frameRepository.findDetailedByIdAndBranchId(1100, branch.getId())).thenReturn(Optional.empty());

    NoSuchElementException exception = assertThrows(
        NoSuchElementException.class,
        () -> frameService.getFrameDetails(1100, "manager@test.com"));

    assertEquals("Frame not found", exception.getMessage());
  }

  @Test
  void getTopPlayersUsesActiveBranchAndSelectedMonth() {
    mockAuthorizedContext();
    LocalDateTime[] capturedRange = new LocalDateTime[2];
    Long[] capturedBranchId = new Long[1];
    List<Map<String, Object>> expected = List.of(Map.of(
        "userId", 91,
        "name", "Winner One",
        "wins", 4L,
        "branchId", branch.getId(),
        "branchName", "Satna",
        "year", 2026,
        "month", 7));

    when(leaderboardCacheService.getTopPlayersForBranchMonth(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
      capturedBranchId[0] = invocation.getArgument(0, Long.class);
      capturedRange[0] = invocation.getArgument(4, LocalDateTime.class);
      capturedRange[1] = invocation.getArgument(5, LocalDateTime.class);
      return expected;
    });

    List<Map<String, Object>> result = frameService.getTopPlayers("manager@test.com", 2026, 7);

    assertEquals(1, result.size());
    assertEquals(91, result.get(0).get("userId"));
    assertEquals("Winner One", result.get(0).get("name"));
    assertEquals(4L, result.get(0).get("wins"));
    assertEquals(branch.getId(), result.get(0).get("branchId"));
    assertEquals("Satna", result.get(0).get("branchName"));
    assertEquals(2026, result.get(0).get("year"));
    assertEquals(7, result.get(0).get("month"));
    assertEquals(branch.getId(), capturedBranchId[0]);
    assertEquals(LocalDateTime.of(2026, 7, 1, 0, 0), capturedRange[0]);
    assertEquals(LocalDateTime.of(2026, 8, 1, 0, 0), capturedRange[1]);
  }

  @Test
  void getTopPlayersDefaultsToCurrentIstMonthWhenMonthNotProvided() {
    mockAuthorizedContext();
    List<Long> repositoryCalls = new ArrayList<>();
    LocalDateTime[] capturedRange = new LocalDateTime[2];

    when(leaderboardCacheService.getTopPlayersForBranchMonth(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
      capturedRange[0] = invocation.getArgument(4, LocalDateTime.class);
      capturedRange[1] = invocation.getArgument(5, LocalDateTime.class);
      repositoryCalls.add(1L);
      return List.of();
    });

    List<Map<String, Object>> result = frameService.getTopPlayers("manager@test.com", null, null);

    YearMonth currentMonth = YearMonth.from(com.youngstersclub.app.util.TimeUtil.nowIST());
    assertTrue(result.isEmpty());
    assertFalse(repositoryCalls.isEmpty());
    assertEquals(currentMonth.atDay(1).atStartOfDay(), capturedRange[0]);
    assertEquals(currentMonth.plusMonths(1).atDay(1).atStartOfDay(), capturedRange[1]);
  }

  @Test
  void getTopPlayersRejectsInvalidMonth() {
    mockAuthorizedContext();

    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> frameService.getTopPlayers("manager@test.com", 2026, 13));

    assertEquals("Month must be between 1 and 12", exception.getMessage());
  }

  private void mockAuthorizedContext() {
    when(userRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(actor));
    when(organizationContextService.resolveContext("manager@test.com")).thenReturn(buildContext());
    when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
        .thenReturn(Optional.of(membership));
    when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branch.getId(), organization.getId()))
        .thenReturn(Optional.of(branch));
  }

  private StartFrameRequest buildStartFrameRequest(Long tableId) {
    StartFrameRequest request = new StartFrameRequest();
    request.setTableId(tableId);
    request.setStartedBy(actor.getId());

    StartFrameRequest.PlayerDto playerOne = new StartFrameRequest.PlayerDto();
    playerOne.setUserId(91);
    playerOne.setName("Player One");

    StartFrameRequest.PlayerDto playerTwo = new StartFrameRequest.PlayerDto();
    playerTwo.setUserId(92);
    playerTwo.setName("Player Two");

    request.setPlayers(List.of(playerOne, playerTwo));
    return request;
  }

  private com.youngstersclub.app.dto.EndFrameTeamRequest buildSingleEndRequest(Integer winnerId, Integer loserId) {
    com.youngstersclub.app.dto.EndFrameTeamRequest request = new com.youngstersclub.app.dto.EndFrameTeamRequest();
    request.setMode("SINGLE");
    request.setWinnerId(winnerId);
    request.setLooserId(loserId);
    return request;
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

  private User buildPlayer(Integer id, String name) {
    User user = new User();
    user.setId(id);
    user.setName(name);
    user.setIsActive(true);
    return user;
  }

  private FrameRepository.TopPlayerProjection topPlayerProjection(Integer userId, String name, Long wins) {
    return new FrameRepository.TopPlayerProjection() {
      @Override
      public Integer getUserId() {
        return userId;
      }

      @Override
      public String getName() {
        return name;
      }

      @Override
      public Long getWins() {
        return wins;
      }
    };
  }

  private Frame buildStartedFrame(Integer frameId, Branch frameBranch, SnookerTable snookerTable) {
    Frame frame = new Frame();
    frame.setId(frameId);
    frame.setBranch(frameBranch);
    frame.setSnookerTable(snookerTable);
    frame.setStartedBy(actor);
    frame.setStatus(FrameStatus.STARTED);
    frame.setPaymentStatus(PaymentStatus.UNPAID);
    frame.setStartTime(LocalDateTime.now().minusMinutes(1));
    return frame;
  }

  private FramePlayer buildFramePlayer(
      Frame frame,
      User user,
      boolean isWinner,
      boolean isLoser,
      BigDecimal amountDue,
      PaymentStatus paymentStatus) {
    FramePlayer framePlayer = new FramePlayer();
    framePlayer.setFrame(frame);
    framePlayer.setUser(user);
    framePlayer.setPlayerName(user.getName());
    framePlayer.setIsWinner(isWinner);
    framePlayer.setIsLoser(isLoser);
    framePlayer.setAmountDue(amountDue);
    framePlayer.setPaymentStatus(paymentStatus);
    return framePlayer;
  }
}
