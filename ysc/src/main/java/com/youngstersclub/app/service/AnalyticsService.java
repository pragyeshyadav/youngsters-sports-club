package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.TodayEarningsDuePlayerDto;
import com.youngstersclub.app.dto.TodayEarningsResponseDto;
import com.youngstersclub.app.repository.FrameRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

    private final FrameRepository frameRepository;

    public AnalyticsService(FrameRepository frameRepository) {
        this.frameRepository = frameRepository;
    }

    public TodayEarningsResponseDto getTodayEarnings() {
        List<FrameRepository.TodayEarningsProjection> rows = frameRepository.findTodayEarningsAnalytics();

        if (rows.isEmpty()) {
            return new TodayEarningsResponseDto(BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }

        FrameRepository.TodayEarningsProjection totalsRow = rows.get(0);
        List<TodayEarningsDuePlayerDto> duePlayers = rows.stream()
                .filter(row -> row.getPlayerName() != null && !row.getPlayerName().isBlank())
                .map(row -> new TodayEarningsDuePlayerDto(
                        row.getPlayerName(),
                        row.getDueAmount() == null ? BigDecimal.ZERO : row.getDueAmount()))
                .toList();

        return new TodayEarningsResponseDto(
                totalsRow.getTotalEarnings() == null ? BigDecimal.ZERO : totalsRow.getTotalEarnings(),
                totalsRow.getTotalDue() == null ? BigDecimal.ZERO : totalsRow.getTotalDue(),
                duePlayers);
    }
}
