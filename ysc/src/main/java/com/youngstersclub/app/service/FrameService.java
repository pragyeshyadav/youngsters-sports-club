package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.StartFrameRequest;
import com.youngstersclub.app.entity.Frame;
import com.youngstersclub.app.entity.FramePlayer;
import com.youngstersclub.app.entity.SnookerTable;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.FrameStatus;
import com.youngstersclub.app.enums.PaymentStatus;
import com.youngstersclub.app.repository.FramePlayerRepository;
import com.youngstersclub.app.repository.FrameRepository;
import com.youngstersclub.app.repository.SnookerTableRepository;
import com.youngstersclub.app.repository.UserRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class FrameService {

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

        SnookerTable table = tableRepository.findById(request.getTableId()).orElseThrow();
        table.setIsAvailable(false);
        tableRepository.save(table);

        User startedBy = userRepository.findById(request.getStartedBy()).orElseThrow();

        Frame frame = new Frame();
        frame.setSnookerTable(table);
        frame.setStartedBy(startedBy);
        frame.setStartTime(LocalDateTime.now());
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

    public Map<String, Object> getActiveFrame(Integer userId) {
        if (userId == null) {
            return null;
        }

        Optional<Frame> frameOpt = frameRepository.findActiveFrameForUser(userId, FrameStatus.STARTED);
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
    }

    public Map<String, Object> getFrameDetails(Integer frameId) {
        if (frameId == null) {
            return null;
        }

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
    }

    public List<Map<String, Object>> getFramePlayers(Integer frameId) {
        if (frameId == null) {
            return List.of();
        }

        return framePlayerRepository.findByFrame_Id(frameId).stream().map(player -> {
            Map<String, Object> playerMap = new HashMap<>();
            playerMap.put("id", player.getId());
            playerMap.put("userId", player.getUser() != null ? player.getUser().getId() : null);
            playerMap.put("playerName", player.getPlayerName());
            return playerMap;
        }).toList();
    }

    public List<Map<String, Object>> getUserFrameHistory(Integer userId) {
        if (userId == null) {
            return List.of();
        }

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
    }

    public BigDecimal getTotalDue(Integer userId) {
        if (userId == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalDue = frameRepository.getTotalDueForUser(userId);
        return totalDue == null ? BigDecimal.ZERO : totalDue;
    }

    public List<Map<String, Object>> getTodayOngoingFrames() {
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
            return frameMap;
        }).toList();
    }

    public List<Map<String, Object>> getTodayCompletedFrames() {
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
    }

    public List<Map<String, Object>> getUserDueFrames(Integer userId) {
        if (userId == null) {
            return List.of();
        }

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
    }

    @Transactional
    public Map<String, Object> endFrame(Integer frameId, Integer winnerId, Integer looserId) {
        if (frameId == null) {
            throw new IllegalArgumentException("Frame id is required");
        }

        Frame frame = frameRepository.findById(frameId)
                .orElseThrow(() -> new RuntimeException("Frame not found"));

        if (frame.getStatus() != FrameStatus.STARTED) {
            throw new RuntimeException("Frame already ended");
        }

        LocalDateTime endTime = LocalDateTime.now();
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

        BigDecimal rate = table.getRatePerMinute();
        BigDecimal totalAmount = rate.multiply(BigDecimal.valueOf(duration));

        if (winnerId != null) {
            User winner = userRepository.findById(winnerId).orElse(null);
            frame.setWinner(winner);
        }

        if (looserId != null) {
            User looser = userRepository.findById(looserId).orElse(null);
            frame.setLooser(looser);
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
        frame.setEndTime(LocalDateTime.now());
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
}
