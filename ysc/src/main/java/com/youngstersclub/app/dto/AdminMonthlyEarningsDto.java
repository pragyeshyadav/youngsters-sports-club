package com.youngstersclub.app.dto;

import java.math.BigDecimal;
import java.util.Map;

public class AdminMonthlyEarningsDto {
    private final BigDecimal currentMonthTotal;
    private final BigDecimal previousMonthTotal;
    private final BigDecimal snookerEarnings;
    private final Map<String, BigDecimal> snookerTableBreakdown;
    private final BigDecimal consumableEarnings;
    private final BigDecimal kidsZoneEarnings;

    public AdminMonthlyEarningsDto(
            BigDecimal currentMonthTotal,
            BigDecimal previousMonthTotal,
            BigDecimal snookerEarnings,
            Map<String, BigDecimal> snookerTableBreakdown,
            BigDecimal consumableEarnings,
            BigDecimal kidsZoneEarnings) {
        this.currentMonthTotal = currentMonthTotal;
        this.previousMonthTotal = previousMonthTotal;
        this.snookerEarnings = snookerEarnings;
        this.snookerTableBreakdown = snookerTableBreakdown;
        this.consumableEarnings = consumableEarnings;
        this.kidsZoneEarnings = kidsZoneEarnings;
    }

    public BigDecimal getCurrentMonthTotal() {
        return currentMonthTotal;
    }

    public BigDecimal getPreviousMonthTotal() {
        return previousMonthTotal;
    }

    public BigDecimal getSnookerEarnings() {
        return snookerEarnings;
    }

    public Map<String, BigDecimal> getSnookerTableBreakdown() {
        return snookerTableBreakdown;
    }

    public BigDecimal getConsumableEarnings() {
        return consumableEarnings;
    }

    public BigDecimal getKidsZoneEarnings() {
        return kidsZoneEarnings;
    }
}
