package com.youngstersclub.app.dto;

import java.math.BigDecimal;

public class UserPaymentSummaryDto {
    private BigDecimal frameDue;
    private BigDecimal consumableDue;
    private BigDecimal totalDue;

    public UserPaymentSummaryDto(BigDecimal frameDue, BigDecimal consumableDue) {
        this.frameDue = frameDue == null ? BigDecimal.ZERO : frameDue;
        this.consumableDue = consumableDue == null ? BigDecimal.ZERO : consumableDue;
        this.totalDue = this.frameDue.add(this.consumableDue);
    }

    public BigDecimal getFrameDue() {
        return frameDue;
    }

    public BigDecimal getConsumableDue() {
        return consumableDue;
    }

    public BigDecimal getTotalDue() {
        return totalDue;
    }
}
