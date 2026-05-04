package com.youngstersclub.app.dto;

import java.math.BigDecimal;

public class TodayEarningsDuePlayerDto {
    private final String name;
    private final BigDecimal due;

    public TodayEarningsDuePlayerDto(String name, BigDecimal due) {
        this.name = name;
        this.due = due;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getDue() {
        return due;
    }
}
