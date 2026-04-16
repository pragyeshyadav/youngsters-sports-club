package com.youngstersclub.app.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ConsumableHistoryRowDto {
    private final String itemName;
    private final Integer quantity;
    private final LocalDateTime date;
    private final BigDecimal amount;
    private final String paymentStatus;

    public ConsumableHistoryRowDto(
            String itemName,
            Integer quantity,
            LocalDateTime date,
            BigDecimal amount,
            String paymentStatus) {
        this.itemName = itemName;
        this.quantity = quantity;
        this.date = date;
        this.amount = amount;
        this.paymentStatus = paymentStatus;
    }

    public String getItemName() {
        return itemName;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }
}
