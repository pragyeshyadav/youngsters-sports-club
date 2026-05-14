package com.youngstersclub.app.dto;

import java.math.BigDecimal;
import java.util.List;

public class TodayEarningsResponseDto {
    private final BigDecimal totalEarnings;
    private final BigDecimal totalDue;
    private final List<TodayEarningsDuePlayerDto> duePlayers;
    private final List<SettledPaymentDto> settledPayments;

    public TodayEarningsResponseDto(
            BigDecimal totalEarnings,
            BigDecimal totalDue,
            List<TodayEarningsDuePlayerDto> duePlayers,
            List<SettledPaymentDto> settledPayments) {
        this.totalEarnings = totalEarnings;
        this.totalDue = totalDue;
        this.duePlayers = duePlayers;
        this.settledPayments = settledPayments;
    }

    public BigDecimal getTotalEarnings() {
        return totalEarnings;
    }

    public BigDecimal getTotalDue() {
        return totalDue;
    }

    public List<TodayEarningsDuePlayerDto> getDuePlayers() {
        return duePlayers;
    }

    public List<SettledPaymentDto> getSettledPayments() {
        return settledPayments;
    }
}
