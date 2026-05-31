package com.youngstersclub.app.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PendingKidsPlayBreakdownDto {
    private final Long sessionId;
    private final String childName;
    private final LocalDateTime date;
    private final BigDecimal amount;

    public PendingKidsPlayBreakdownDto(Long sessionId, String childName, LocalDateTime date, BigDecimal amount) {
        this.sessionId = sessionId;
        this.childName = childName;
        this.date = date;
        this.amount = amount;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public String getChildName() {
        return childName;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
