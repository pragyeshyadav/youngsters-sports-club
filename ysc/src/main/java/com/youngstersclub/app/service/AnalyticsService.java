package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.TodayEarningsDuePlayerDto;
import com.youngstersclub.app.dto.TodayEarningsResponseDto;
import com.youngstersclub.app.repository.FrameRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

    private final FrameRepository frameRepository;

    public AnalyticsService(FrameRepository frameRepository) {
        this.frameRepository = frameRepository;
    }

    public TodayEarningsResponseDto getTodayEarnings() {
        return getEarningsForDate(LocalDate.now());
    }

    public TodayEarningsResponseDto getEarningsForDate(LocalDate requestedDate) {
        LocalDate today = LocalDate.now();
        LocalDate selectedDate = requestedDate == null ? today : requestedDate;
        LocalDate oldestAllowedDate = today.minusDays(60);

        if (selectedDate.isAfter(today)) {
            throw new IllegalArgumentException("Future dates are not allowed");
        }

        if (selectedDate.isBefore(oldestAllowedDate)) {
            throw new IllegalArgumentException("Please select a date within the last 60 days");
        }

        List<FrameRepository.TodayEarningsProjection> rows = selectedDate.equals(today)
                ? frameRepository.findTodayEarningsAnalytics()
                : frameRepository.findEarningsAnalyticsByDate(selectedDate);

        if (rows.isEmpty()) {
            return new TodayEarningsResponseDto(BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }

        FrameRepository.TodayEarningsProjection totalsRow = rows.get(0);
        List<TodayEarningsDuePlayerDto> duePlayers = rows.stream()
                .filter(row -> row.getPlayerName() != null && !row.getPlayerName().isBlank())
                .map(row -> new TodayEarningsDuePlayerDto(
                        row.getUserId(),
                        row.getPlayerName(),
                        row.getDueAmount() == null ? BigDecimal.ZERO : row.getDueAmount()))
                .toList();

        return new TodayEarningsResponseDto(
                totalsRow.getTotalEarnings() == null ? BigDecimal.ZERO : totalsRow.getTotalEarnings(),
                totalsRow.getTotalDue() == null ? BigDecimal.ZERO : totalsRow.getTotalDue(),
                duePlayers);
    }
}
