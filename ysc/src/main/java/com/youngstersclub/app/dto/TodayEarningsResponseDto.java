package com.youngstersclub.app.dto;

import java.math.BigDecimal;
import java.util.List;

public class TodayEarningsResponseDto {
    private final BigDecimal totalEarnings;
    private final BigDecimal totalDue;
    private final List<TodayEarningsDuePlayerDto> duePlayers;

    public TodayEarningsResponseDto(
            BigDecimal totalEarnings,
            BigDecimal totalDue,
            List<TodayEarningsDuePlayerDto> duePlayers) {
        this.totalEarnings = totalEarnings;
        this.totalDue = totalDue;
        this.duePlayers = duePlayers;
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
}
