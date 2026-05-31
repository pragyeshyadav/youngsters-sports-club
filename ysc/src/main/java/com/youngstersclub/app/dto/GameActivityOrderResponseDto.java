package com.youngstersclub.app.dto;

import java.math.BigDecimal;

public class GameActivityOrderResponseDto {
    private final int orderCount;
    private final BigDecimal totalAmount;

    public GameActivityOrderResponseDto(int orderCount, BigDecimal totalAmount) {
        this.orderCount = orderCount;
        this.totalAmount = totalAmount;
    }

    public int getOrderCount() {
        return orderCount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
}
