package com.youngstersclub.app.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PendingFrameBreakdownDto {
    private final Integer frameId;
    private final String matchup;
    private final LocalDateTime date;
    private final BigDecimal dueAmount;

    public PendingFrameBreakdownDto(Integer frameId, String matchup, LocalDateTime date, BigDecimal dueAmount) {
        this.frameId = frameId;
        this.matchup = matchup;
        this.date = date;
        this.dueAmount = dueAmount;
    }

    public Integer getFrameId() {
        return frameId;
    }

    public String getMatchup() {
        return matchup;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public BigDecimal getDueAmount() {
        return dueAmount;
    }
}
