package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.TodayEarningsDuePlayerDto;
import com.youngstersclub.app.dto.TodayEarningsResponseDto;
import com.youngstersclub.app.dto.SettledPaymentDto;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.repository.FrameRepository;
import com.youngstersclub.app.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

    private final FrameRepository frameRepository;
    private final PaymentRepository paymentRepository;
    private final GameActivityService gameActivityService;
    private final com.youngstersclub.app.repository.UserRepository userRepository;

    public AnalyticsService(
            FrameRepository frameRepository,
            PaymentRepository paymentRepository,
            GameActivityService gameActivityService,
            com.youngstersclub.app.repository.UserRepository userRepository) {
        this.frameRepository = frameRepository;
        this.paymentRepository = paymentRepository;
        this.gameActivityService = gameActivityService;
        this.userRepository = userRepository;
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
        LocalDateTime startDateTime = selectedDate.atStartOfDay();
        LocalDateTime endDateTime = selectedDate.plusDays(1).atStartOfDay();
        BigDecimal activityEarnings = gameActivityService.getGrossEarningsBetween(startDateTime, endDateTime);
        BigDecimal activityDue = gameActivityService.getTotalUnpaidDueBetween(startDateTime, endDateTime);
        Map<Integer, BigDecimal> activityDueByUser = gameActivityService.getUnpaidDueByUserForDate(selectedDate);
        List<SettledPaymentDto> settledPayments = paymentRepository.findSettledPaymentsByDate(selectedDate).stream()
                .map(payment -> new SettledPaymentDto(
                        payment.getUserName(),
                        payment.getPaidAmount() == null ? BigDecimal.ZERO : payment.getPaidAmount(),
                        payment.getDiscount() == null ? BigDecimal.ZERO : payment.getDiscount(),
                        payment.getDate()))
                .toList();

        BigDecimal baseEarnings = rows.isEmpty() || rows.get(0).getTotalEarnings() == null
                ? BigDecimal.ZERO
                : rows.get(0).getTotalEarnings();
        BigDecimal baseDue = rows.isEmpty() || rows.get(0).getTotalDue() == null
                ? BigDecimal.ZERO
                : rows.get(0).getTotalDue();

        Map<Integer, TodayEarningsDuePlayerDto> duePlayersByUser = new LinkedHashMap<>();
        for (FrameRepository.TodayEarningsProjection row : rows) {
            if (row.getUserId() == null || row.getPlayerName() == null || row.getPlayerName().isBlank()) {
                continue;
            }
            duePlayersByUser.put(
                    row.getUserId(),
                    new TodayEarningsDuePlayerDto(
                            row.getUserId(),
                            row.getPlayerName(),
                            row.getDueAmount() == null ? BigDecimal.ZERO : row.getDueAmount()));
        }

        if (!activityDueByUser.isEmpty()) {
            Map<Integer, String> userNames = userRepository.findAllById(activityDueByUser.keySet()).stream()
                    .collect(java.util.stream.Collectors.toMap(User::getId, User::getName));
            activityDueByUser.forEach((userId, amount) -> {
                if (userId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                    return;
                }
                TodayEarningsDuePlayerDto existing = duePlayersByUser.get(userId);
                if (existing != null) {
                    duePlayersByUser.put(
                            userId,
                            new TodayEarningsDuePlayerDto(userId, existing.getName(), existing.getDue().add(amount)));
                } else {
                    duePlayersByUser.put(
                            userId,
                            new TodayEarningsDuePlayerDto(
                                    userId,
                                    userNames.getOrDefault(userId, "Customer"),
                                    amount));
                }
            });
        }

        return new TodayEarningsResponseDto(
                baseEarnings.add(activityEarnings),
                baseDue.add(activityDue),
                duePlayersByUser.values().stream().toList(),
                settledPayments);
    }
}
