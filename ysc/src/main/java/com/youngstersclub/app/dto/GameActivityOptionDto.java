package com.youngstersclub.app.dto;

import java.math.BigDecimal;

public class GameActivityOptionDto {
    private final Long id;
    private final String gameName;
    private final BigDecimal basePricePerMinute;

    public GameActivityOptionDto(Long id, String gameName, BigDecimal basePricePerMinute) {
        this.id = id;
        this.gameName = gameName;
        this.basePricePerMinute = basePricePerMinute;
    }

    public Long getId() {
        return id;
    }

    public String getGameName() {
        return gameName;
    }

    public BigDecimal getBasePricePerMinute() {
        return basePricePerMinute;
    }
}
