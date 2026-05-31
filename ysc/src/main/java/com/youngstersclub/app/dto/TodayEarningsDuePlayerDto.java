package com.youngstersclub.app.dto;

import java.math.BigDecimal;

public class TodayEarningsDuePlayerDto {
    private final Integer userId;
    private final String name;
    private final BigDecimal due;

    public TodayEarningsDuePlayerDto(Integer userId, String name, BigDecimal due) {
        this.userId = userId;
        this.name = name;
        this.due = due;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getDue() {
        return due;
    }
}
