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
        frame.setStatus(FrameStatus.ENDED);
        frameRepository.save(frame);

        table.setIsAvailable(true);
        tableRepository.save(table);

        Map<String, Object> response = new HashMap<>();
        response.put("duration", duration);
        response.put("amount", totalAmount);
        response.put("frameId", frame.getId());
        response.put("tableId", table.getId());
        return response;
    }
}
