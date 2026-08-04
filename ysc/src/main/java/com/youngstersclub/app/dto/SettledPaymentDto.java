package com.youngstersclub.app.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SettledPaymentDto {
    private final String userName;
    private final BigDecimal paidAmount;
    private final BigDecimal discount;
    private final LocalDateTime date;
    private final String paymentMethod;

    public SettledPaymentDto(
            String userName,
            BigDecimal paidAmount,
            BigDecimal discount,
            LocalDateTime date,
            String paymentMethod) {
        this.userName = userName;
        this.paidAmount = paidAmount;
        this.discount = discount;
        this.date = date;
        this.paymentMethod = paymentMethod;
    }

    public String getUserName() {
        return userName;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }
}
