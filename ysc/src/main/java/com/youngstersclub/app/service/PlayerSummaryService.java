package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.PlayerSummaryBaseProjection;
import com.youngstersclub.app.dto.PlayerSummaryDto;
import com.youngstersclub.app.dto.UserPaymentSummaryDto;
import com.youngstersclub.app.repository.UserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class PlayerSummaryService {

    private final UserRepository userRepository;
    private final UserPaymentSummaryService userPaymentSummaryService;

    public PlayerSummaryService(
            UserRepository userRepository,
            UserPaymentSummaryService userPaymentSummaryService) {
        this.userRepository = userRepository;
        this.userPaymentSummaryService = userPaymentSummaryService;
    }

    public Page<PlayerSummaryDto> getPlayerSummaries(Pageable pageable) {
        List<PlayerSummaryBaseProjection> basePlayers = userRepository.getAllPlayerSummaryBases();
        List<Integer> userIds = basePlayers.stream()
                .map(PlayerSummaryBaseProjection::getUserId)
                .toList();

        Map<Integer, UserPaymentSummaryDto> summariesByUserId = userPaymentSummaryService.getPaymentSummaries(userIds);

        List<PlayerSummaryDto> sortedPlayers = basePlayers.stream()
                .map(player -> new PlayerSummaryDto(
                        player.getUserId(),
                        player.getName(),
                        player.getEmail(),
                        player.getFramesPlayed(),
                        summariesByUserId.getOrDefault(player.getUserId(), new UserPaymentSummaryDto(null, null, null)).getTotalDue()))
                .sorted(Comparator.comparing(PlayerSummaryDto::getTotalDue, Comparator.reverseOrder())
                        .thenComparing(PlayerSummaryDto::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .collect(Collectors.toList());

        int start = Math.min((int) pageable.getOffset(), sortedPlayers.size());
        int end = Math.min(start + pageable.getPageSize(), sortedPlayers.size());
        return new PageImpl<>(sortedPlayers.subList(start, end), pageable, sortedPlayers.size());
    }
}
