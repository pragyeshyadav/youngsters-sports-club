package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.SnookerTableResponseDto;
import com.youngstersclub.app.dto.SnookerTableStatusDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.Frame;
import com.youngstersclub.app.entity.FramePlayer;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.SnookerTable;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.entity.UserBranchAccess;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.FrameRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.SnookerTableRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SnookerTableService {

  private static final Logger log = LoggerFactory.getLogger(SnookerTableService.class);

  private final SnookerTableRepository snookerTableRepository;
  private final FrameRepository frameRepository;
  private final UserRepository userRepository;
  private final OrganizationContextService organizationContextService;
  private final OrganizationUserRepository organizationUserRepository;
  private final BranchRepository branchRepository;
  private final UserBranchAccessRepository userBranchAccessRepository;

  public SnookerTableService(
      SnookerTableRepository snookerTableRepository,
      FrameRepository frameRepository,
      UserRepository userRepository,
      OrganizationContextService organizationContextService,
      OrganizationUserRepository organizationUserRepository,
      BranchRepository branchRepository,
      UserBranchAccessRepository userBranchAccessRepository) {
    this.snookerTableRepository = snookerTableRepository;
    this.frameRepository = frameRepository;
    this.userRepository = userRepository;
    this.organizationContextService = organizationContextService;
    this.organizationUserRepository = organizationUserRepository;
    this.branchRepository = branchRepository;
    this.userBranchAccessRepository = userBranchAccessRepository;
  }

  @Transactional(readOnly = true)
  public List<SnookerTableResponseDto> getCurrentBranchAvailableTables(String actorEmail) {
    ActiveTableContext context = resolveActiveContext(actorEmail);
    List<SnookerTable> tables = snookerTableRepository.findAvailableTablesSafeByBranchId(context.branch().getId());
    log.info(
        "action=LIST_SNOOKER_TABLES organizationId={} branchId={} actorUserId={} tableCount={}",
        context.organizationId(),
        context.branch().getId(),
        context.actor().getId(),
        tables.size());
    return tables.stream().map(this::toTableResponseDto).toList();
  }

  @Transactional(readOnly = true)
  public List<SnookerTableStatusDto> getCurrentBranchTableStatuses(String actorEmail) {
    ActiveTableContext context = resolveActiveContext(actorEmail);
    List<SnookerTable> branchTables =
        snookerTableRepository.findByBranch_IdAndIsActiveTrueOrderByIdAsc(context.branch().getId());
    List<Frame> activeFrames = frameRepository.findAllOngoingFramesByBranchId(context.branch().getId());

    Map<Long, Frame> tableActiveFrameMap = new HashMap<>();
    for (Frame frame : activeFrames) {
      if (frame.getSnookerTable() != null && frame.getSnookerTable().getId() != null) {
        tableActiveFrameMap.put(frame.getSnookerTable().getId(), frame);
      }
    }

    List<SnookerTableStatusDto> statuses = new ArrayList<>();
    for (SnookerTable table : branchTables) {
      List<String> players = new ArrayList<>();
      Frame activeFrame = tableActiveFrameMap.get(table.getId());
      if (activeFrame != null && activeFrame.getFramePlayers() != null) {
        for (FramePlayer framePlayer : activeFrame.getFramePlayers()) {
          String playerName =
              framePlayer.getUser() != null ? framePlayer.getUser().getName() : framePlayer.getPlayerName();
          if (playerName != null && !playerName.isBlank()) {
            players.add(playerName);
          }
        }
      }
      statuses.add(
          new SnookerTableStatusDto(
              table.getId(),
              table.getTableName(),
              table.getIsAvailable(),
              context.branch().getId(),
              context.branch().getName(),
              players));
    }

    log.info(
        "action=LIST_SNOOKER_TABLE_STATUSES organizationId={} branchId={} actorUserId={} tableCount={}",
        context.organizationId(),
        context.branch().getId(),
        context.actor().getId(),
        statuses.size());
    return statuses;
  }

  @Transactional(readOnly = true)
  public SnookerTable requireCurrentBranchTable(Long tableId, String actorEmail) {
    ActiveTableContext context = resolveActiveContext(actorEmail);
    SnookerTable table =
        snookerTableRepository
            .findByIdAndBranch_Id(tableId, context.branch().getId())
            .orElseThrow(() -> {
              log.warn(
                  "action=DENY_CROSS_BRANCH_TABLE_ACCESS organizationId={} branchId={} requestedTableId={} actorUserId={}",
                  context.organizationId(),
                  context.branch().getId(),
                  tableId,
                  context.actor().getId());
              return new java.util.NoSuchElementException("Snooker table not found");
            });

    log.info(
        "action=GET_SNOOKER_TABLE organizationId={} branchId={} tableId={} actorUserId={}",
        context.organizationId(),
        context.branch().getId(),
        tableId,
        context.actor().getId());
    return table;
  }

  private SnookerTableResponseDto toTableResponseDto(SnookerTable table) {
    Long branchId = table.getBranch() == null ? null : table.getBranch().getId();
    String branchName = table.getBranch() == null ? null : table.getBranch().getName();
    return new SnookerTableResponseDto(
        table.getId(),
        table.getTableName(),
        table.getRatePerMinute(),
        table.getIsActive(),
        table.getIsAvailable(),
        branchId,
        branchName);
  }

  private ActiveTableContext resolveActiveContext(String actorEmail) {
    String normalizedEmail = actorEmail == null ? "" : actorEmail.trim().toLowerCase();
    if (normalizedEmail.isEmpty()) {
      throw new SecurityException("Authenticated user email is required");
    }

    User actor =
        userRepository
            .findByEmail(normalizedEmail)
            .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
            .orElseThrow(() -> new SecurityException("Authenticated user not found"));

    OrganizationContextDto context = organizationContextService.resolveContext(normalizedEmail);
    if (context.getCurrentOrganization() == null || context.getCurrentBranch() == null) {
      throw new IllegalArgumentException("Current organization and branch context are required");
    }

    UserRole actorRole =
        context.getCurrentRole() == null || context.getCurrentRole().isBlank()
            ? actor.getRole()
            : UserRole.valueOf(context.getCurrentRole());
    if (actorRole == null) {
      throw new SecurityException("You are not authorized to access snooker tables");
    }

    OrganizationUser membership =
        organizationUserRepository
            .findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), context.getCurrentOrganization().getId())
            .orElseThrow(() -> new java.util.NoSuchElementException("Caller organization membership not found"));

    Branch branch =
        branchRepository
            .findByIdAndOrganizationIdAndIsActiveTrue(
                context.getCurrentBranch().getId(), context.getCurrentOrganization().getId())
            .orElseThrow(() -> new java.util.NoSuchElementException("Current branch not found"));

    boolean branchAccessible =
        membership.getBaseBranch() != null && branch.getId().equals(membership.getBaseBranch().getId());
    if (!branchAccessible) {
      branchAccessible =
          userBranchAccessRepository.existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(
              membership.getId(), branch.getId());
    }

    if (!branchAccessible) {
      throw new SecurityException("You do not have access to the current branch");
    }

    return new ActiveTableContext(
        actor,
        membership,
        context.getCurrentOrganization().getId(),
        context.getCurrentOrganization().getName(),
        branch,
        actorRole);
  }

  private record ActiveTableContext(
      User actor,
      OrganizationUser membership,
      Long organizationId,
      String organizationName,
      Branch branch,
      UserRole role) {}
}
