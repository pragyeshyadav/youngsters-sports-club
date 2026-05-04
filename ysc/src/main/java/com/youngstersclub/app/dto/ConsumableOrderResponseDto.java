package com.youngstersclub.app.dto;

import java.math.BigDecimal;

public class ConsumableOrderResponseDto {
    private Long orderId;
    private BigDecimal totalAmount;

    public ConsumableOrderResponseDto(Long orderId, BigDecimal totalAmount) {
        this.orderId = orderId;
        this.totalAmount = totalAmount;
    }

    public Long getOrderId() {
        return orderId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
}
