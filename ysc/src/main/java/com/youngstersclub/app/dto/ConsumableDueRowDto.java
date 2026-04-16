package com.youngstersclub.app.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ConsumableDueRowDto {
    private Long orderId;
    private String itemName;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal totalCost;
    private LocalDateTime createdAt;

    public ConsumableDueRowDto(
            Long orderId,
            String itemName,
            Integer quantity,
            BigDecimal price,
            BigDecimal totalCost,
            LocalDateTime createdAt) {
        this.orderId = orderId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.price = price;
        this.totalCost = totalCost;
        this.createdAt = createdAt;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getItemName() {
        return itemName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
