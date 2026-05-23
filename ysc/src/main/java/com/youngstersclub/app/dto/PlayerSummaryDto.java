package com.youngstersclub.app.dto;

import java.math.BigDecimal;

public class PlayerSummaryDto {
    private final Integer userId;
    private final String name;
    private final String email;
    private final Long framesPlayed;
    private final BigDecimal totalDue;

    public PlayerSummaryDto(Integer userId, String name, String email, Long framesPlayed, BigDecimal totalDue) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.framesPlayed = framesPlayed;
        this.totalDue = totalDue == null ? BigDecimal.ZERO : totalDue;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Long getFramesPlayed() {
        return framesPlayed;
    }

    public BigDecimal getTotalDue() {
        return totalDue;
    }
}
