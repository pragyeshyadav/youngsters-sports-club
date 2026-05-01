package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.AdminMonthlyEarningsDto;
import com.youngstersclub.app.repository.ConsumableOrderRepository;
import com.youngstersclub.app.repository.FrameRepository;
import com.youngstersclub.app.repository.KidsPlaySessionRepository;
import com.youngstersclub.app.util.TimeUtil;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AdminAnalyticsService {

    private final FrameRepository frameRepository;
    private final ConsumableOrderRepository consumableOrderRepository;
    private final KidsPlaySessionRepository kidsPlaySessionRepository;

    public AdminAnalyticsService(
            FrameRepository frameRepository,
            ConsumableOrderRepository consumableOrderRepository,
            KidsPlaySessionRepository kidsPlaySessionRepository) {
        this.frameRepository = frameRepository;
        this.consumableOrderRepository = consumableOrderRepository;
        this.kidsPlaySessionRepository = kidsPlaySessionRepository;
    }

    public AdminMonthlyEarningsDto getMonthlyEarnings(int month, int year) {
        validateMonthYear(month, year);

        LocalDate today = TimeUtil.nowIST().toLocalDate();
        LocalDate selectedMonthStart = LocalDate.of(year, month, 1);
        LocalDate selectedMonthEnd = selectedMonthStart.withDayOfMonth(selectedMonthStart.lengthOfMonth());
        LocalDate selectedEffectiveEnd = selectedMonthEnd.isAfter(today) ? today : selectedMonthEnd;

        LocalDate previousMonthStart = selectedMonthStart.minusMonths(1);
        LocalDate previousMonthEnd = previousMonthStart.withDayOfMonth(previousMonthStart.lengthOfMonth());
        LocalDate previousEffectiveEnd = previousMonthEnd.isAfter(today) ? today : previousMonthEnd;

        BigDecimal snookerEarnings = sumOrZero(frameRepository.getCompletedEarningsBetween(
                selectedMonthStart.atStartOfDay(),
                selectedEffectiveEnd.plusDays(1).atStartOfDay()));
        BigDecimal consumableEarnings = sumOrZero(consumableOrderRepository.getPaidEarningsBetween(
                selectedMonthStart.atStartOfDay(),
                selectedEffectiveEnd.plusDays(1).atStartOfDay()));
        BigDecimal kidsZoneEarnings = sumOrZero(kidsPlaySessionRepository.getPaidEarningsBetween(
                selectedMonthStart.atStartOfDay(),
                selectedEffectiveEnd.plusDays(1).atStartOfDay()));
        Map<String, BigDecimal> snookerTableBreakdown = frameRepository
                .getCompletedEarningsByTableBetween(
                        selectedMonthStart.atStartOfDay(),
                        selectedEffectiveEnd.plusDays(1).atStartOfDay())
                .stream()
                .collect(
                        LinkedHashMap::new,
                        (map, row) -> map.put(row.getTableName(), sumOrZero(row.getTotal())),
                        LinkedHashMap::putAll);

        BigDecimal currentMonthTotal = snookerEarnings.add(consumableEarnings).add(kidsZoneEarnings);

        BigDecimal previousSnooker = previousEffectiveEnd.isBefore(previousMonthStart)
                ? BigDecimal.ZERO
                : sumOrZero(frameRepository.getCompletedEarningsBetween(
                        previousMonthStart.atStartOfDay(),
                        previousEffectiveEnd.plusDays(1).atStartOfDay()));
        BigDecimal previousConsumables = previousEffectiveEnd.isBefore(previousMonthStart)
                ? BigDecimal.ZERO
                : sumOrZero(consumableOrderRepository.getPaidEarningsBetween(
                        previousMonthStart.atStartOfDay(),
                        previousEffectiveEnd.plusDays(1).atStartOfDay()));
        BigDecimal previousKids = previousEffectiveEnd.isBefore(previousMonthStart)
                ? BigDecimal.ZERO
                : sumOrZero(kidsPlaySessionRepository.getPaidEarningsBetween(
                        previousMonthStart.atStartOfDay(),
                        previousEffectiveEnd.plusDays(1).atStartOfDay()));

        BigDecimal previousMonthTotal = previousSnooker.add(previousConsumables).add(previousKids);

        return new AdminMonthlyEarningsDto(
                currentMonthTotal,
                previousMonthTotal,
                snookerEarnings,
                snookerTableBreakdown,
                consumableEarnings,
                kidsZoneEarnings);
    }

    private void validateMonthYear(int month, int year) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }

        int currentYear = TimeUtil.nowIST().getYear();
        if (year < currentYear - 1 || year > currentYear) {
            throw new IllegalArgumentException("Year must be current year or previous year");
        }
    }

    private BigDecimal sumOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
