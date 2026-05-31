package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.StartFrameRequest;
import com.youngstersclub.app.dto.PendingFrameBreakdownDto;
import com.youngstersclub.app.entity.Frame;
import com.youngstersclub.app.entity.FramePlayer;
import com.youngstersclub.app.entity.SnookerTable;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.FrameStatus;
import com.youngstersclub.app.enums.PaymentStatus;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.FramePlayerRepository;
import com.youngstersclub.app.repository.FrameRepository;
import com.youngstersclub.app.repository.SnookerTableRepository;
import com.youngstersclub.app.repository.UserRepository;
import com.youngstersclub.app.util.TimeUtil;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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

    public FrameService(
            SnookerTableRepository tableRepository,
            FrameRepository frameRepository,
            FramePlayerRepository framePlayerRepository,
            UserRepository userRepository) {
        this.tableRepository = tableRepository;
        this.frameRepository = frameRepository;
        this.framePlayerRepository = framePlayerRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Integer startFrame(StartFrameRequest request) {
        if (request == null || request.getTableId() == null || request.getStartedBy() == null) {
            throw new IllegalArgumentException("Missing start frame details");
        }

        List<StartFrameRequest.PlayerDto> players = request.getPlayers();
        if (players == null || players.isEmpty()) {
            throw new IllegalArgumentException("At least one player is required");
        }

        User startedBy = userRepository.findById(request.getStartedBy()).orElseThrow();
        List<SnookerTable> availableTables = tableRepository.findByIsAvailableTrueOrderByIdAsc();
        boolean isPrivileged = PRIVILEGED_ROLES.contains(startedBy.getRole());

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

        SnookerTable table = tableRepository.findById(requestedTableId).orElseThrow();
        if (!Boolean.TRUE.equals(table.getIsAvailable())) {
            throw new RuntimeException("Table is not available");
        }

        table.setIsAvailable(false);
        tableRepository.save(table);

        Frame frame = new Frame();
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

        return frame.getId();
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

    public Map<String, Object> getFrameDetails(Integer frameId) {
        if (frameId == null) {
            return null;
        }

        return executeWithRetry("getFrameDetails", () -> {
            Optional<Frame> frameOpt = frameRepository.findDetailedById(frameId);
            if (frameOpt.isEmpty()) {
                return null;
            }

            Frame frame = frameOpt.get();
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
        if (frameId == null) {
            return List.of();
        }

        return executeWithRetry("getFramePlayers", () -> {
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

    public List<Map<String, Object>> getTodayOngoingFrames() {
        return executeWithRetry("getTodayOngoingFrames", () -> {
            LocalDate today = LocalDate.now();
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

            return frameRepository.findTodayOngoingFrames(startOfDay, endOfDay).stream().map(frame -> {
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

    public List<Map<String, Object>> getTodayCompletedFrames() {
        return executeWithRetry("getTodayCompletedFrames", () -> {
            LocalDate today = LocalDate.now();
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

            return frameRepository.findTodayCompletedFrames(startOfDay, endOfDay).stream().map(frame -> {
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

    public List<Map<String, Object>> getCompletedFramesByDate(LocalDate selectedDate) {
        LocalDate targetDate = selectedDate == null ? TimeUtil.nowIST().toLocalDate() : selectedDate;

        return executeWithRetry("getCompletedFramesByDate", () -> {
            LocalDateTime startOfDay = targetDate.atStartOfDay();
            LocalDateTime endOfDay = targetDate.plusDays(1).atStartOfDay();

            return frameRepository.findTodayCompletedFrames(startOfDay, endOfDay).stream().map(frame -> {
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

    @Transactional
    public Map<String, Object> endFrame(Integer frameId, com.youngstersclub.app.dto.EndFrameTeamRequest request) {
        if (frameId == null) {
            throw new IllegalArgumentException("Frame id is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("End frame details are required");
        }

        Frame frame = frameRepository.findById(frameId)
                .orElseThrow(() -> new RuntimeException("Frame not found"));

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

        table.setIsAvailable(true);
        tableRepository.save(table);

        Map<String, Object> response = new HashMap<>();
        response.put("duration", duration);
        response.put("amount", totalAmount);
        response.put("frameId", frame.getId());
        response.put("tableId", table.getId());
        response.put("paymentDue", frame.getPaymentDue());
        return response;
    }

    private Map<Integer, BigDecimal> splitAmounts(BigDecimal totalAmount, List<Integer> loserIds) {
        Map<Integer, BigDecimal> amounts = new HashMap<>();
        if (totalAmount == null || loserIds == null || loserIds.isEmpty()) {
            return amounts;
        }

        int loserCount = loserIds.size();
        BigDecimal baseShare = totalAmount.divide(BigDecimal.valueOf(loserCount), 2, RoundingMode.DOWN);
        BigDecimal distributed = BigDecimal.ZERO;

        for (int index = 0; index < loserCount; index++) {
            Integer loserId = loserIds.get(index);
            BigDecimal share = index == loserCount - 1
                    ? totalAmount.subtract(distributed).setScale(2, RoundingMode.HALF_UP)
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

    public List<Map<String, Object>> getTopPlayers() {
        return executeWithRetry("getTopPlayers", () -> {
            return frameRepository.findTopPlayersOfCurrentMonth().stream().map(projection -> {
                Map<String, Object> map = new HashMap<>();
                map.put("name", projection.getName());
                map.put("wins", projection.getWins());
                return map;
            }).toList();
        });
    }
}
