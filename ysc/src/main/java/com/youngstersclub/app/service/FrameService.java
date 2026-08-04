package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.StartFrameRequest;
import com.youngstersclub.app.dto.PendingFrameBreakdownDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.Frame;
import com.youngstersclub.app.entity.FramePlayer;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.SnookerTable;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.entity.UserBranchAccess;
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
import com.youngstersclub.app.util.TimeUtil;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.Supplier;

@Service
public class FrameService {

    private static final Logger log = LoggerFactory.getLogger(FrameService.class);

    private static final EnumSet<UserRole> PRIVILEGED_ROLES =
            EnumSet.of(UserRole.MANAGER, UserRole.ADMIN, UserRole.SUPER_ADMIN);

    private final SnookerTableRepository tableRepository;
    private final FrameRepository frameRepository;
    private final FramePlayerRepository framePlayerRepository;
    private final UserRepository userRepository;
    private final OrganizationContextService organizationContextService;
    private final OrganizationUserRepository organizationUserRepository;
    private final BranchRepository branchRepository;
    private final UserBranchAccessRepository userBranchAccessRepository;
    private final UserDueService userDueService;

    public FrameService(
            SnookerTableRepository tableRepository,
            FrameRepository frameRepository,
            FramePlayerRepository framePlayerRepository,
            UserRepository userRepository,
            OrganizationContextService organizationContextService,
            OrganizationUserRepository organizationUserRepository,
            BranchRepository branchRepository,
            UserBranchAccessRepository userBranchAccessRepository,
            UserDueService userDueService) {
        this.tableRepository = tableRepository;
        this.frameRepository = frameRepository;
        this.framePlayerRepository = framePlayerRepository;
        this.userRepository = userRepository;
        this.organizationContextService = organizationContextService;
        this.organizationUserRepository = organizationUserRepository;
        this.branchRepository = branchRepository;
        this.userBranchAccessRepository = userBranchAccessRepository;
        this.userDueService = userDueService;
    }

    @Transactional
    public Integer startFrame(StartFrameRequest request, String actorEmail) {
        if (request == null || request.getTableId() == null) {
            throw new IllegalArgumentException("Missing start frame details");
        }

        List<StartFrameRequest.PlayerDto> players = request.getPlayers();
        if (players == null || players.isEmpty()) {
            throw new IllegalArgumentException("At least one player is required");
        }

        FrameOperationContext context = resolveFrameOperationContext(actorEmail);
        User startedBy = context.actor();
        Branch currentBranch = context.branch();
        List<SnookerTable> availableTables = tableRepository.findAvailableTablesSafeByBranchId(currentBranch.getId());
        boolean isPrivileged = PRIVILEGED_ROLES.contains(context.role());

        if (availableTables.isEmpty()) {
            throw new RuntimeException("No table available");
        }

        Long requestedTableId = request.getTableId();
        if (!isPrivileged) {
            boolean userAlreadyHasRunningFrame =
                    frameRepository.findActiveFrameForUser(startedBy.getId(), FrameStatus.STARTED).isPresent();
            if (userAlreadyHasRunningFrame) {
                throw new RuntimeException("You already have an ongoing frame");
            }
        }

        if (requestedTableId == null) {
            throw new IllegalArgumentException("Table id is required");
        }

        SnookerTable table = tableRepository
                .findByIdAndBranch_IdAndIsActiveTrue(requestedTableId, currentBranch.getId())
                .orElseThrow(() -> {
                    log.warn(
                            "action=DENY_START_FRAME requestedTableId={} branchId={} actorUserId={} reason=CROSS_BRANCH_TABLE",
                            requestedTableId,
                            currentBranch.getId(),
                            startedBy.getId());
                    return new NoSuchElementException("Table not found");
                });
        if (!Boolean.TRUE.equals(table.getIsAvailable())) {
            throw new RuntimeException("Table is not available");
        }

        table.setIsAvailable(false);
        tableRepository.save(table);

        Frame frame = new Frame();
        frame.setBranch(currentBranch);
        frame.setSnookerTable(table);
        frame.setStartedBy(startedBy);
        frame.setStartTime(TimeUtil.nowIST());
        frame.setStatus(FrameStatus.STARTED);
        frame.setPaymentStatus(PaymentStatus.UNPAID);
        frame = frameRepository.save(frame);

        for (StartFrameRequest.PlayerDto playerDto : players) {
            FramePlayer framePlayer = new FramePlayer();
            framePlayer.setFrame(frame);
            framePlayer.setPlayerName(playerDto.getName());

            if (playerDto.getUserId() != null) {
                User player = userRepository.findById(playerDto.getUserId()).orElseThrow();
                framePlayer.setUser(player);
            }

            framePlayerRepository.save(framePlayer);
        }

        log.info(
                "action=START_FRAME organizationId={} branchId={} tableId={} frameId={} playerCount={} actorUserId={}",
                context.organizationId(),
                currentBranch.getId(),
                table.getId(),
                frame.getId(),
                players.size(),
                startedBy.getId());
        return frame.getId();
    }

    private FrameOperationContext resolveFrameOperationContext(String actorEmail) {
        String normalizedEmail = actorEmail == null ? "" : actorEmail.trim().toLowerCase();
        if (normalizedEmail.isEmpty()) {
            throw new SecurityException("Authenticated user email is required");
        }

        User actor = userRepository.findByEmail(normalizedEmail)
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .orElseThrow(() -> new SecurityException("Authenticated user not found"));

        OrganizationContextDto context = organizationContextService.resolveContext(normalizedEmail);
        if (context.getCurrentOrganization() == null || context.getCurrentBranch() == null) {
            throw new IllegalArgumentException("Current organization and branch context are required");
        }

        UserRole actorRole = context.getCurrentRole() == null || context.getCurrentRole().isBlank()
                ? actor.getRole()
                : UserRole.valueOf(context.getCurrentRole());

        OrganizationUser membership = organizationUserRepository
                .findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), context.getCurrentOrganization().getId())
                .orElseThrow(() -> new NoSuchElementException("Caller organization membership not found"));

        Branch branch = branchRepository
                .findByIdAndOrganizationIdAndIsActiveTrue(
                        context.getCurrentBranch().getId(),
                        context.getCurrentOrganization().getId())
                .orElseThrow(() -> new NoSuchElementException("Current branch not found"));

        boolean branchAccessible = membership.getBaseBranch() != null
                && branch.getId().equals(membership.getBaseBranch().getId());
        if (!branchAccessible) {
            branchAccessible = userBranchAccessRepository
                    .existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(membership.getId(), branch.getId());
        }

        if (!branchAccessible) {
            throw new SecurityException("You do not have access to the current branch");
        }

        return new FrameOperationContext(actor, membership, branch, context.getCurrentOrganization().getId(), actorRole);
    }

    private record FrameOperationContext(
            User actor,
            OrganizationUser membership,
            Branch branch,
            Long organizationId,
            UserRole role) {
    }

    private <T> T executeWithRetry(String operationName, Supplier<T> action) {
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return action.get();
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    log.error("DB connection failed after {} attempts for {}", maxRetries, operationName, e);
                    throw e;
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("DB retry interrupted", ie);
                }
            }
        }
        return null;
    }

    public Map<String, Object> getActiveFrame(Integer userId) {
        if (userId == null) {
            return null;
        }

        return executeWithRetry("getActiveFrame", () -> {
            Optional<Frame> frameOpt = frameRepository.findActiveFrameForUser(userId, FrameStatus.STARTED);
            if (frameOpt.isEmpty()) {
                return null;
            }

            Frame frame = frameOpt.get();
            List<FramePlayer> players = frame.getFramePlayers() == null ? List.of() : frame.getFramePlayers();

            Map<String, Object> frameDetails = new HashMap<>();
            frameDetails.put("id", frame.getId());
            frameDetails.put("tableId", frame.getSnookerTable() != null ? frame.getSnookerTable().getId() : null);
            frameDetails.put("tableName", frame.getSnookerTable() != null ? frame.getSnookerTable().getTableName() : null);
            frameDetails.put("startTime", frame.getStartTime());
            frameDetails.put("status", frame.getStatus());

            List<Map<String, Object>> playerDetails = players.stream().map(player -> {
                Map<String, Object> playerMap = new HashMap<>();
                playerMap.put("id", player.getId());
                playerMap.put("userId", player.getUser() != null ? player.getUser().getId() : null);
                playerMap.put("playerName", player.getPlayerName());
                return playerMap;
            }).toList();

            Map<String, Object> response = new HashMap<>();
            response.put("frame", frameDetails);
            response.put("players", playerDetails);
            return response;
        });
    }

    public Map<String, Object> getUserOngoingFrame(Integer userId) {
        return getActiveFrame(userId);
    }

    public Map<String, Object> getFrameDetails(Integer frameId, String actorEmail) {
        if (frameId == null) {
            return null;
        }

        return executeWithRetry("getFrameDetails", () -> {
            FrameOperationContext context = resolveFrameOperationContext(actorEmail);
            Frame frame = frameRepository.findDetailedByIdAndBranchId(frameId, context.branch().getId())
                    .orElseThrow(() -> new NoSuchElementException("Frame not found"));
            List<FramePlayer> players = framePlayerRepository.findByFrame_Id(frame.getId());

            Map<String, Object> frameDetails = new HashMap<>();
            frameDetails.put("id", frame.getId());
            frameDetails.put("tableId", frame.getSnookerTable() != null ? frame.getSnookerTable().getId() : null);
            frameDetails.put("tableName", frame.getSnookerTable() != null ? frame.getSnookerTable().getTableName() : null);
            frameDetails.put("startTime", frame.getStartTime());
            frameDetails.put("status", frame.getStatus());
            frameDetails.put("endTime", frame.getEndTime());

            List<Map<String, Object>> playerDetails = players.stream().map(player -> {
                Map<String, Object> playerMap = new HashMap<>();
                playerMap.put("id", player.getId());
                playerMap.put("userId", player.getUser() != null ? player.getUser().getId() : null);
                playerMap.put("playerName", player.getPlayerName());
                return playerMap;
            }).toList();

            Map<String, Object> response = new HashMap<>();
            response.put("frame", frameDetails);
            response.put("players", playerDetails);
            return response;
        });
    }

    public List<Map<String, Object>> getFramePlayers(Integer frameId) {
        return getFramePlayers(frameId, null);
    }

    public List<Map<String, Object>> getFramePlayers(Integer frameId, String actorEmail) {
        if (frameId == null) {
            return List.of();
        }

        return executeWithRetry("getFramePlayers", () -> {
            if (actorEmail != null && !actorEmail.isBlank()) {
                FrameOperationContext context = resolveFrameOperationContext(actorEmail);
                frameRepository.findByIdAndBranch_Id(frameId, context.branch().getId())
                        .orElseThrow(() -> new NoSuchElementException("Frame not found"));
            }

            return framePlayerRepository.findByFrame_Id(frameId).stream().map(player -> {
                Map<String, Object> playerMap = new HashMap<>();
                playerMap.put("id", player.getId());
                playerMap.put("userId", player.getUser() != null ? player.getUser().getId() : null);
                playerMap.put("playerName", player.getPlayerName());
                return playerMap;
            }).toList();
        });
    }

    public List<Map<String, Object>> getUserFrameHistory(Integer userId) {
        if (userId == null) {
            return List.of();
        }

        return executeWithRetry("getUserFrameHistory", () -> {
            return frameRepository.findUserFrameHistory(userId).stream().map(frame -> {
                Map<String, Object> frameMap = new HashMap<>();
                frameMap.put("frameId", frame.getId());
                frameMap.put("startTime", frame.getStartTime());
                frameMap.put("endTime", frame.getEndTime());
                frameMap.put("duration", frame.getDurationMinutes());
                frameMap.put("amount", frame.getTotalAmount());
                frameMap.put("paymentDue", frame.getPaymentDue());
                frameMap.put("winnerName", frame.getWinner() != null ? frame.getWinner().getName() : null);
                frameMap.put("looserName", frame.getLooser() != null ? frame.getLooser().getName() : null);
                return frameMap;
            }).toList();
        });
    }

    public BigDecimal getTotalDue(Integer userId) {
        if (userId == null) {
            return BigDecimal.ZERO;
        }

        return executeWithRetry("getTotalDue", () -> {
            BigDecimal totalDue = frameRepository.getTotalDueForUser(userId);
            return totalDue == null ? BigDecimal.ZERO : totalDue;
        });
    }

    public List<Map<String, Object>> getTodayOngoingFrames(String actorEmail) {
        return executeWithRetry("getTodayOngoingFrames", () -> {
            FrameOperationContext context = resolveFrameOperationContext(actorEmail);
            LocalDate today = TimeUtil.nowIST().toLocalDate();
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

            return frameRepository.findTodayOngoingFramesByBranchId(context.branch().getId(), startOfDay, endOfDay).stream().map(frame -> {
                Map<String, Object> frameMap = new HashMap<>();
                frameMap.put("id", frame.getId());
                frameMap.put("tableId", frame.getSnookerTable() != null ? frame.getSnookerTable().getId() : null);
                frameMap.put("tableName", frame.getSnookerTable() != null ? frame.getSnookerTable().getTableName() : null);
                frameMap.put("startTime", frame.getStartTime());
                frameMap.put("status", frame.getStatus());
                frameMap.put("startedBy", frame.getStartedBy() != null ? frame.getStartedBy().getName() : null);
                frameMap.put(
                        "players",
                        frame.getFramePlayers() == null
                                ? List.of()
                                : frame.getFramePlayers().stream()
                                        .map(player -> player.getUser() != null
                                                ? player.getUser().getName()
                                                : player.getPlayerName())
                                        .filter(playerName -> playerName != null && !playerName.isBlank())
                                        .distinct()
                                        .toList());
                return frameMap;
            }).toList();
        });
    }

    public List<Map<String, Object>> getTodayCompletedFrames(String actorEmail) {
        return executeWithRetry("getTodayCompletedFrames", () -> {
            FrameOperationContext context = resolveFrameOperationContext(actorEmail);
            LocalDate today = TimeUtil.nowIST().toLocalDate();
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

            return frameRepository.findTodayCompletedFramesByBranchId(context.branch().getId(), startOfDay, endOfDay).stream().map(frame -> {
                Map<String, Object> frameMap = new HashMap<>();
                frameMap.put("id", frame.getId());
                frameMap.put("winnerName", frame.getWinner() != null ? frame.getWinner().getName() : null);
                frameMap.put("looserName", frame.getLooser() != null ? frame.getLooser().getName() : null);
                frameMap.put("startTime", frame.getStartTime());
                frameMap.put("endTime", frame.getEndTime());
                frameMap.put("durationMinutes", frame.getDurationMinutes());
                frameMap.put("totalAmount", frame.getTotalAmount());
                frameMap.put("paymentDue", frame.getPaymentDue());
                return frameMap;
            }).toList();
        });
    }

    public List<Map<String, Object>> getCompletedFramesByDate(LocalDate selectedDate, String actorEmail) {
        LocalDate targetDate = selectedDate == null ? TimeUtil.nowIST().toLocalDate() : selectedDate;

        return executeWithRetry("getCompletedFramesByDate", () -> {
            FrameOperationContext context = resolveFrameOperationContext(actorEmail);
            LocalDateTime startOfDay = targetDate.atStartOfDay();
            LocalDateTime endOfDay = targetDate.plusDays(1).atStartOfDay();

            return frameRepository.findTodayCompletedFramesByBranchId(context.branch().getId(), startOfDay, endOfDay).stream().map(frame -> {
                Map<String, Object> frameMap = new HashMap<>();
                frameMap.put("id", frame.getId());
                frameMap.put("winnerName", frame.getWinner() != null ? frame.getWinner().getName() : null);
                frameMap.put("looserName", frame.getLooser() != null ? frame.getLooser().getName() : null);
                frameMap.put("startTime", frame.getStartTime());
                frameMap.put("endTime", frame.getEndTime());
                frameMap.put("durationMinutes", frame.getDurationMinutes());
                frameMap.put("totalAmount", frame.getTotalAmount());
                frameMap.put("paymentDue", frame.getPaymentDue());
                return frameMap;
            }).toList();
        });
    }

    public List<Map<String, Object>> getUserDueFrames(Integer userId) {
        if (userId == null) {
            return List.of();
        }

        return executeWithRetry("getUserDueFrames", () -> {
            return frameRepository.findDueFramesByUser(userId).stream().map(frame -> {
                Map<String, Object> frameMap = new HashMap<>();
                frameMap.put("frameId", frame.getId());
                frameMap.put("startTime", frame.getStartTime());
                frameMap.put("endTime", frame.getEndTime());
                frameMap.put("duration", frame.getDurationMinutes());
                frameMap.put("amount", frame.getTotalAmount());
                frameMap.put("paymentDue", frame.getPaymentDue());
                frameMap.put("winnerName", frame.getWinner() != null ? frame.getWinner().getName() : null);
                frameMap.put("looserName", frame.getLooser() != null ? frame.getLooser().getName() : null);
                return frameMap;
            }).toList();
        });
    }

    public List<Map<String, Object>> getUserDueFrames(Integer userId, String actorEmail) {
        if (userId == null) {
            return List.of();
        }

        FrameOperationContext context = resolveFrameOperationContext(actorEmail);
        return executeWithRetry("getUserDueFramesByBranch", () ->
                frameRepository.findDueFramesByUserAndBranch(userId, context.branch().getId()).stream().map(frame -> {
                    Map<String, Object> frameMap = new HashMap<>();
                    frameMap.put("frameId", frame.getId());
                    frameMap.put("startTime", frame.getStartTime());
                    frameMap.put("endTime", frame.getEndTime());
                    frameMap.put("duration", frame.getDurationMinutes());
                    frameMap.put("amount", frame.getTotalAmount());
                    frameMap.put("paymentDue", getDueAmountForUser(frame, userId));
                    frameMap.put("winnerName", frame.getWinner() != null ? frame.getWinner().getName() : null);
                    frameMap.put("looserName", frame.getLooser() != null ? frame.getLooser().getName() : null);
                    return frameMap;
                }).toList());
    }

    public List<PendingFrameBreakdownDto> getUserDueFramesByDate(Integer userId, LocalDate selectedDate) {
        if (userId == null || selectedDate == null) {
            return List.of();
        }

        return executeWithRetry("getUserDueFramesByDate", () ->
                frameRepository.findDueFramesByUserOrderByStartTime(userId).stream()
                        .filter(frame -> frame.getStartTime() != null && selectedDate.equals(frame.getStartTime().toLocalDate()))
                        .map(frame -> new PendingFrameBreakdownDto(
                                frame.getId(),
                                buildMatchupLabel(frame),
                                frame.getEndTime() != null ? frame.getEndTime() : frame.getStartTime(),
                                getDueAmountForUser(frame, userId)))
                        .toList());
    }

    public List<PendingFrameBreakdownDto> getUserDueFramesByDate(Integer userId, LocalDate selectedDate, Long branchId) {
        if (userId == null || selectedDate == null || branchId == null) {
            return List.of();
        }

        return executeWithRetry("getUserDueFramesByDateAndBranch", () ->
                frameRepository.findDueFramesByUserAndBranchOrderByStartTime(userId, branchId).stream()
                        .filter(frame -> frame.getStartTime() != null && selectedDate.equals(frame.getStartTime().toLocalDate()))
                        .map(frame -> new PendingFrameBreakdownDto(
                                frame.getId(),
                                buildMatchupLabel(frame),
                                frame.getEndTime() != null ? frame.getEndTime() : frame.getStartTime(),
                                getDueAmountForUser(frame, userId)))
                        .toList());
    }

    @Transactional
    public Map<String, Object> endFrame(Integer frameId, com.youngstersclub.app.dto.EndFrameTeamRequest request, String actorEmail) {
        if (frameId == null) {
            throw new IllegalArgumentException("Frame id is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("End frame details are required");
        }

        FrameOperationContext context = resolveFrameOperationContext(actorEmail);
        Frame frame = frameRepository.findForUpdateByIdAndBranchId(frameId, context.branch().getId())
                .orElseThrow(() -> {
                    log.warn(
                            "action=DENY_END_FRAME organizationId={} activeBranchId={} requestedFrameId={} actorUserId={} reason=FRAME_NOT_IN_ACTIVE_BRANCH",
                            context.organizationId(),
                            context.branch().getId(),
                            frameId,
                            context.actor().getId());
                    return new NoSuchElementException("Frame not found");
                });

        Branch historicalBranch = frame.getBranch();
        if (historicalBranch == null) {
            throw new IllegalStateException("Frame branch is missing");
        }
        if (!historicalBranch.getId().equals(context.branch().getId())) {
            log.warn(
                    "action=DENY_END_FRAME organizationId={} activeBranchId={} requestedFrameId={} actorUserId={} reason=FRAME_BRANCH_MISMATCH",
                    context.organizationId(),
                    context.branch().getId(),
                    frameId,
                    context.actor().getId());
            throw new NoSuchElementException("Frame not found");
        }
        if (historicalBranch.getOrganization() == null
                || !context.organizationId().equals(historicalBranch.getOrganization().getId())) {
            throw new SecurityException("Active organization does not match frame organization");
        }

        if (frame.getStatus() != FrameStatus.STARTED) {
            throw new RuntimeException("Frame already ended");
        }

        LocalDateTime endTime = TimeUtil.nowIST();
        frame.setEndTime(endTime);

        long duration = Duration.between(frame.getStartTime(), endTime).toMinutes();
        if (duration <= 0) {
            duration = 1;
        }

        frame.setDurationMinutes((int) duration);

        SnookerTable table = frame.getSnookerTable();
        if (table == null) {
            throw new RuntimeException("Table not found");
        }
        if (table.getBranch() == null || !historicalBranch.getId().equals(table.getBranch().getId())) {
            throw new IllegalStateException("Frame table does not belong to the frame branch");
        }

        List<FramePlayer> framePlayers = framePlayerRepository.findByFrame_Id(frameId);
        int playerCount = framePlayers != null ? framePlayers.size() : 0;

        BigDecimal baseRate = table.getRatePerMinute();
        BigDecimal effectiveRate = baseRate;

        if (playerCount > 2) {
            BigDecimal extraPlayers = BigDecimal.valueOf(playerCount - 2);
            BigDecimal extraCharge = extraPlayers.multiply(BigDecimal.valueOf(0.5));
            effectiveRate = baseRate.add(extraCharge);
        }

        BigDecimal totalAmount = effectiveRate.multiply(BigDecimal.valueOf(duration));

        List<Integer> winnerIds = request.getWinnerIds();
        List<Integer> loserIds = request.getLoserIds();
        Set<Integer> framePlayerIds = new HashSet<>();
        for (FramePlayer framePlayer : framePlayers) {
            if (framePlayer.getUser() != null && framePlayer.getUser().getId() != null) {
                framePlayerIds.add(framePlayer.getUser().getId());
            }
        }

        String requestedMode = request.getMode() == null ? "SINGLE" : request.getMode().trim().toUpperCase();
        boolean canUseTeamMode = playerCount == 4;
        boolean isTeamGame = "TEAM".equals(requestedMode);

        if (!"SINGLE".equals(requestedMode) && !"TEAM".equals(requestedMode)) {
            log.warn("Invalid game mode {} for frame {}", requestedMode, frameId);
            throw new IllegalArgumentException("Invalid game mode");
        }

        if (isTeamGame && !canUseTeamMode) {
            log.warn("Team mode requested for unsupported frame {} with playerCount {}", frameId, playerCount);
            throw new IllegalArgumentException("Team mode is only available for 4-player frames");
        }

        if (isTeamGame) {
            if (winnerIds == null || loserIds == null || winnerIds.size() != 2 || loserIds.size() != 2) {
                log.warn("Invalid team selection for frame {}. winners={}, losers={}", frameId, winnerIds, loserIds);
                throw new IllegalArgumentException("Team mode requires exactly 2 winners and 2 losers");
            }

            Set<Integer> selectedIds = new HashSet<>();
            selectedIds.addAll(winnerIds);
            selectedIds.addAll(loserIds);

            if (selectedIds.size() != 4) {
                log.warn("Non-unique team selection for frame {}. winners={}, losers={}", frameId, winnerIds, loserIds);
                throw new IllegalArgumentException("Winners and losers must be unique in team mode");
            }

            if (!framePlayerIds.containsAll(selectedIds)) {
                log.warn("Team selection contains players outside frame {}. selectedIds={}, framePlayerIds={}", frameId, selectedIds, framePlayerIds);
                throw new IllegalArgumentException("Selected players do not belong to this frame");
            }

            Map<Integer, BigDecimal> splitAmounts = splitAmounts(totalAmount, loserIds);

            for (FramePlayer fp : framePlayers) {
                if (fp.getUser() != null) {
                    if (winnerIds.contains(fp.getUser().getId())) {
                        fp.setIsWinner(true);
                        fp.setIsLoser(false);
                        fp.setAmountDue(BigDecimal.ZERO);
                        fp.setPaymentStatus(PaymentStatus.PAID);
                    } else if (loserIds.contains(fp.getUser().getId())) {
                        fp.setIsWinner(false);
                        fp.setIsLoser(true);
                        fp.setAmountDue(splitAmounts.getOrDefault(fp.getUser().getId(), BigDecimal.ZERO));
                        fp.setPaymentStatus(PaymentStatus.UNPAID);
                    }
                }
                framePlayerRepository.save(fp);
            }

            frame.setWinner(userRepository.findById(winnerIds.get(0)).orElse(null));
            frame.setLooser(userRepository.findById(loserIds.get(0)).orElse(null));
        } else {
            Integer winnerId = request.getWinnerId();
            Integer looserId = request.getLooserId();
            List<Integer> dynamicLoserIds = request.getLoserIds();

            if (playerCount == 3) {
                if (winnerId == null) {
                    log.warn("Missing winner for 3-player frame {}", frameId);
                    throw new IllegalArgumentException("3-player frames require exactly 1 winner");
                }
                if (dynamicLoserIds == null || dynamicLoserIds.isEmpty() || dynamicLoserIds.size() > 2) {
                    log.warn("Invalid loser selection for 3-player frame {}. losers={}", frameId, dynamicLoserIds);
                    throw new IllegalArgumentException("3-player frames require 1 or 2 losers");
                }
                if (dynamicLoserIds.contains(winnerId)) {
                    log.warn("Winner overlaps losers for 3-player frame {}. winner={}, losers={}", frameId, winnerId, dynamicLoserIds);
                    throw new IllegalArgumentException("Winner and losers must be different players");
                }
                if (!framePlayerIds.contains(winnerId) || !framePlayerIds.containsAll(dynamicLoserIds)) {
                    log.warn("3-player selection contains players outside frame {}. winner={}, losers={}, framePlayers={}", frameId, winnerId, dynamicLoserIds, framePlayerIds);
                    throw new IllegalArgumentException("Selected players do not belong to this frame");
                }
                if (new HashSet<>(dynamicLoserIds).size() != dynamicLoserIds.size()) {
                    log.warn("Duplicate losers selected for 3-player frame {}. losers={}", frameId, dynamicLoserIds);
                    throw new IllegalArgumentException("Losers must be unique");
                }

                Map<Integer, BigDecimal> splitAmounts = splitAmounts(totalAmount, dynamicLoserIds);
                frame.setWinner(userRepository.findById(winnerId).orElse(null));
                frame.setLooser(userRepository.findById(dynamicLoserIds.get(0)).orElse(null));

                for (FramePlayer fp : framePlayers) {
                    if (fp.getUser() == null || fp.getUser().getId() == null) {
                        framePlayerRepository.save(fp);
                        continue;
                    }
                    Integer playerId = fp.getUser().getId();
                    if (playerId.equals(winnerId)) {
                        fp.setIsWinner(true);
                        fp.setIsLoser(false);
                        fp.setAmountDue(BigDecimal.ZERO);
                        fp.setPaymentStatus(PaymentStatus.PAID);
                    } else if (dynamicLoserIds.contains(playerId)) {
                        fp.setIsWinner(false);
                        fp.setIsLoser(true);
                        fp.setAmountDue(splitAmounts.getOrDefault(playerId, BigDecimal.ZERO));
                        fp.setPaymentStatus(PaymentStatus.UNPAID);
                    } else {
                        fp.setIsWinner(false);
                        fp.setIsLoser(false);
                        fp.setAmountDue(BigDecimal.ZERO);
                        fp.setPaymentStatus(PaymentStatus.PAID);
                    }
                    framePlayerRepository.save(fp);
                }
            } else if (playerCount == 5 || playerCount == 6) {
                if (dynamicLoserIds == null || dynamicLoserIds.isEmpty() || dynamicLoserIds.size() > 3) {
                    log.warn("Invalid loser selection for {}-player frame {}. losers={}", playerCount, frameId, dynamicLoserIds);
                    throw new IllegalArgumentException("5 or 6-player frames require 1 to 3 losers");
                }
                if (!framePlayerIds.containsAll(dynamicLoserIds)) {
                    log.warn("Multi-player selection contains players outside frame {}. losers={}, framePlayers={}", frameId, dynamicLoserIds, framePlayerIds);
                    throw new IllegalArgumentException("Selected players do not belong to this frame");
                }
                if (new HashSet<>(dynamicLoserIds).size() != dynamicLoserIds.size()) {
                    log.warn("Duplicate losers selected for frame {}. losers={}", frameId, dynamicLoserIds);
                    throw new IllegalArgumentException("Losers must be unique");
                }

                Set<Integer> winnerIdSet = new HashSet<>(framePlayerIds);
                winnerIdSet.removeAll(dynamicLoserIds);
                if (winnerIdSet.isEmpty()) {
                    log.warn("No winners remain after loser selection for frame {}. losers={}, framePlayers={}", frameId, dynamicLoserIds, framePlayerIds);
                    throw new IllegalArgumentException("At least one winner is required");
                }

                Map<Integer, BigDecimal> splitAmounts = splitAmounts(totalAmount, dynamicLoserIds);
                Integer primaryWinnerId = winnerIdSet.iterator().next();
                frame.setWinner(userRepository.findById(primaryWinnerId).orElse(null));
                frame.setLooser(userRepository.findById(dynamicLoserIds.get(0)).orElse(null));

                for (FramePlayer fp : framePlayers) {
                    if (fp.getUser() == null || fp.getUser().getId() == null) {
                        framePlayerRepository.save(fp);
                        continue;
                    }
                    Integer playerId = fp.getUser().getId();
                    if (dynamicLoserIds.contains(playerId)) {
                        fp.setIsWinner(false);
                        fp.setIsLoser(true);
                        fp.setAmountDue(splitAmounts.getOrDefault(playerId, BigDecimal.ZERO));
                        fp.setPaymentStatus(PaymentStatus.UNPAID);
                    } else {
                        fp.setIsWinner(true);
                        fp.setIsLoser(false);
                        fp.setAmountDue(BigDecimal.ZERO);
                        fp.setPaymentStatus(PaymentStatus.PAID);
                    }
                    framePlayerRepository.save(fp);
                }
            } else {
                if (winnerId == null || looserId == null) {
                    log.warn("Single mode selection incomplete for frame {}. winner={}, loser={}", frameId, winnerId, looserId);
                    throw new IllegalArgumentException("Single mode requires exactly 1 winner and 1 loser");
                }

                if (winnerId.equals(looserId)) {
                    log.warn("Winner and loser are identical for frame {}. playerId={}", frameId, winnerId);
                    throw new IllegalArgumentException("Winner and loser must be different players");
                }

                if (!framePlayerIds.contains(winnerId) || !framePlayerIds.contains(looserId)) {
                    log.warn("Single mode selection contains players outside frame {}. winner={}, loser={}, framePlayers={}", frameId, winnerId, looserId, framePlayerIds);
                    throw new IllegalArgumentException("Selected players do not belong to this frame");
                }

                User winner = userRepository.findById(winnerId).orElse(null);
                frame.setWinner(winner);
                User looser = userRepository.findById(looserId).orElse(null);
                frame.setLooser(looser);

                if (framePlayers != null) {
                    for (FramePlayer fp : framePlayers) {
                        if (fp.getUser() != null && looserId != null && fp.getUser().getId().equals(looserId)) {
                            fp.setIsWinner(false);
                            fp.setIsLoser(true);
                            fp.setAmountDue(totalAmount);
                            fp.setPaymentStatus(PaymentStatus.UNPAID);
                        } else if (fp.getUser() != null && winnerId != null && fp.getUser().getId().equals(winnerId)) {
                            fp.setIsWinner(true);
                            fp.setIsLoser(false);
                            fp.setAmountDue(BigDecimal.ZERO);
                            fp.setPaymentStatus(PaymentStatus.PAID);
                        } else if (fp.getUser() != null) {
                            fp.setIsWinner(false);
                            fp.setIsLoser(false);
                            fp.setAmountDue(BigDecimal.ZERO);
                            fp.setPaymentStatus(PaymentStatus.PAID);
                        }
                        framePlayerRepository.save(fp);
                    }
                }
            }
        }

        frame.setTotalAmount(totalAmount);
        frame.setPaymentDue(totalAmount);
        frame.setStatus(FrameStatus.ENDED);
        frameRepository.save(frame);
        syncBranchScopedUserDues(framePlayers, historicalBranch);

        table.setIsAvailable(true);
        tableRepository.save(table);

        log.info(
                "action=END_FRAME organizationId={} branchId={} frameId={} tableId={} actorUserId={} playerCount={} winnerCount={} loserCount={} totalAmount={}",
                context.organizationId(),
                historicalBranch.getId(),
                frame.getId(),
                table.getId(),
                context.actor().getId(),
                playerCount,
                countWinners(framePlayers),
                countLosers(framePlayers),
                totalAmount);

        Map<String, Object> response = new HashMap<>();
        response.put("duration", duration);
        response.put("amount", totalAmount);
        response.put("frameId", frame.getId());
        response.put("tableId", table.getId());
        response.put("paymentDue", frame.getPaymentDue());
        response.put("branchId", historicalBranch.getId());
        response.put("branchName", historicalBranch.getName());
        response.put("status", frame.getStatus());
        response.put("tableName", table.getTableName());
        response.put("endedAt", frame.getEndTime());
        return response;
    }

    private void syncBranchScopedUserDues(List<FramePlayer> framePlayers, Branch branch) {
        if (framePlayers == null || framePlayers.isEmpty() || branch == null) {
            return;
        }

        for (FramePlayer framePlayer : framePlayers) {
            if (framePlayer.getUser() == null || framePlayer.getUser().getId() == null) {
                continue;
            }
            userDueService.syncBranchDue(framePlayer.getUser(), branch);

            log.info(
                    "action=GENERATE_FRAME_DUE branchId={} frameId={} customerId={} amount={}",
                    branch.getId(),
                    framePlayer.getFrame() != null ? framePlayer.getFrame().getId() : null,
                    framePlayer.getUser().getId(),
                    framePlayer.getAmountDue());
        }
    }

    private int countWinners(List<FramePlayer> framePlayers) {
        if (framePlayers == null) {
            return 0;
        }
        return (int) framePlayers.stream().filter(player -> Boolean.TRUE.equals(player.getIsWinner())).count();
    }

    private int countLosers(List<FramePlayer> framePlayers) {
        if (framePlayers == null) {
            return 0;
        }
        return (int) framePlayers.stream().filter(player -> Boolean.TRUE.equals(player.getIsLoser())).count();
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP) : amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private Map<Integer, BigDecimal> splitAmounts(BigDecimal totalAmount, List<Integer> loserIds) {
        Map<Integer, BigDecimal> amounts = new HashMap<>();
        if (totalAmount == null || loserIds == null || loserIds.isEmpty()) {
            return amounts;
        }

        int loserCount = loserIds.size();
        BigDecimal baseShare = totalAmount.divide(BigDecimal.valueOf(loserCount), 2, java.math.RoundingMode.DOWN);
        BigDecimal distributed = BigDecimal.ZERO;

        for (int index = 0; index < loserCount; index++) {
            Integer loserId = loserIds.get(index);
            BigDecimal share = index == loserCount - 1
                    ? totalAmount.subtract(distributed).setScale(2, java.math.RoundingMode.HALF_UP)
                    : baseShare;
            amounts.put(loserId, share);
            distributed = distributed.add(share);
        }

        return amounts;
    }

    private BigDecimal getDueAmountForUser(Frame frame, Integer userId) {
        if (frame == null || userId == null) {
            return BigDecimal.ZERO;
        }

        if (frame.getFramePlayers() != null) {
            for (FramePlayer framePlayer : frame.getFramePlayers()) {
                if (framePlayer.getUser() != null
                        && userId.equals(framePlayer.getUser().getId())
                        && framePlayer.getAmountDue() != null
                        && framePlayer.getAmountDue().compareTo(BigDecimal.ZERO) > 0) {
                    return framePlayer.getAmountDue();
                }
            }
        }

        return frame.getPaymentDue() == null ? BigDecimal.ZERO : frame.getPaymentDue();
    }

    private String buildMatchupLabel(Frame frame) {
        if (frame == null) {
            return "Frame";
        }

        List<FramePlayer> players = frame.getFramePlayers() == null ? List.of() : frame.getFramePlayers();
        List<String> winners = players.stream()
                .filter(player -> Boolean.TRUE.equals(player.getIsWinner()))
                .map(this::resolvePlayerName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
        List<String> losers = players.stream()
                .filter(player -> Boolean.TRUE.equals(player.getIsLoser()))
                .map(this::resolvePlayerName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();

        if (!winners.isEmpty() && !losers.isEmpty()) {
            return String.join(" & ", winners) + " vs " + String.join(" & ", losers);
        }

        if (frame.getWinner() != null && frame.getLooser() != null) {
            return frame.getWinner().getName() + " vs " + frame.getLooser().getName();
        }

        List<String> allPlayers = players.stream()
                .map(this::resolvePlayerName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .collect(Collectors.toList());
        if (allPlayers.size() >= 2) {
            return String.join(" vs ", allPlayers);
        }
        return "Frame #" + frame.getId();
    }

    private String resolvePlayerName(FramePlayer framePlayer) {
        if (framePlayer == null) {
            return null;
        }
        if (framePlayer.getUser() != null && framePlayer.getUser().getName() != null && !framePlayer.getUser().getName().isBlank()) {
            return framePlayer.getUser().getName();
        }
        return framePlayer.getPlayerName();
    }

    @Transactional
    public void rejectFrame(Integer frameId) {
        if (frameId == null) {
            throw new IllegalArgumentException("Frame id is required");
        }

        Frame frame = frameRepository.findDetailedById(frameId)
                .orElseThrow(() -> new RuntimeException("Frame not found"));

        if (frame.getStatus() != FrameStatus.STARTED) {
            throw new RuntimeException("Only ongoing frames can be rejected");
        }

        frame.setStatus(FrameStatus.REJECTED);
        frame.setEndTime(TimeUtil.nowIST());
        frame.setDurationMinutes(0);
        frame.setTotalAmount(BigDecimal.ZERO);
        frame.setPaymentDue(BigDecimal.ZERO);
        frameRepository.save(frame);

        SnookerTable table = frame.getSnookerTable();
        if (table != null) {
            table.setIsAvailable(true);
            tableRepository.save(table);
        }
    }

    public List<Map<String, Object>> getAllTableStatuses() {
        return executeWithRetry("getAllTableStatuses", () -> {
            List<SnookerTable> allTables = tableRepository.findAll();
            List<Frame> activeFrames = frameRepository.findAllOngoingFrames();
            
            Map<Long, Frame> tableActiveFrameMap = new HashMap<>();
            if (activeFrames != null) {
                for (Frame f : activeFrames) {
                    if (f.getSnookerTable() != null) {
                        tableActiveFrameMap.put(f.getSnookerTable().getId(), f);
                    }
                }
            }
            
            List<Map<String, Object>> statuses = new java.util.ArrayList<>();
            for (SnookerTable table : allTables) {
                Map<String, Object> map = new HashMap<>();
                map.put("tableName", table.getTableName());
                map.put("isAvailable", table.getIsAvailable());
                
                List<String> players = new java.util.ArrayList<>();
                if (!Boolean.TRUE.equals(table.getIsAvailable()) && tableActiveFrameMap.containsKey(table.getId())) {
                    Frame f = tableActiveFrameMap.get(table.getId());
                    if (f.getFramePlayers() != null) {
                        for(FramePlayer fp : f.getFramePlayers()) {
                           players.add(fp.getUser() != null ? fp.getUser().getName() : fp.getPlayerName());
                        }
                    }
                }
                map.put("players", players);
                statuses.add(map);
            }
            return statuses;
        });
    }

    public List<Map<String, Object>> getTopPlayers(String actorEmail, Integer year, Integer month) {
        return executeWithRetry("getTopPlayers", () -> {
            FrameOperationContext context = resolveFrameOperationContext(actorEmail);
            YearMonth selectedMonth = resolveLeaderboardMonth(year, month);
            LocalDateTime startInclusive = selectedMonth.atDay(1).atStartOfDay();
            LocalDateTime endExclusive = selectedMonth.plusMonths(1).atDay(1).atStartOfDay();

            List<Map<String, Object>> result = frameRepository
                    .findTopPlayersOfMonthByBranch(context.branch().getId(), startInclusive, endExclusive)
                    .stream().map(projection -> {
                Map<String, Object> map = new HashMap<>();
                map.put("userId", projection.getUserId());
                map.put("name", projection.getName());
                map.put("wins", projection.getWins());
                map.put("branchId", context.branch().getId());
                map.put("branchName", context.branch().getName());
                map.put("year", selectedMonth.getYear());
                map.put("month", selectedMonth.getMonthValue());
                return map;
            }).toList();

            log.info(
                    "action=GET_MONTHLY_TOP10_LEADERBOARD organizationId={} branchId={} year={} month={} actorUserId={} resultCount={}",
                    context.organizationId(),
                    context.branch().getId(),
                    selectedMonth.getYear(),
                    selectedMonth.getMonthValue(),
                    context.actor().getId(),
                    result.size());
            return result;
        });
    }

    private YearMonth resolveLeaderboardMonth(Integer year, Integer month) {
        YearMonth currentMonth = YearMonth.from(TimeUtil.nowIST());
        if (year == null && month == null) {
            return currentMonth;
        }
        if (year == null || month == null) {
            throw new IllegalArgumentException("Both year and month are required together");
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
        if (year < 2000 || year > currentMonth.getYear() + 1) {
            throw new IllegalArgumentException("Year is out of supported range");
        }
        return YearMonth.of(year, month);
    }
}
