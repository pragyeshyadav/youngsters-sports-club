package com.youngstersclub.app.dto;

import java.math.BigDecimal;

public class UserPaymentSummaryDto {
    private BigDecimal frameDue;
    private BigDecimal consumableDue;
    private BigDecimal kidsDue;
    private BigDecimal totalDue;

    public UserPaymentSummaryDto(BigDecimal frameDue, BigDecimal consumableDue, BigDecimal kidsDue) {
        this.frameDue = frameDue == null ? BigDecimal.ZERO : frameDue;
        this.consumableDue = consumableDue == null ? BigDecimal.ZERO : consumableDue;
        this.kidsDue = kidsDue == null ? BigDecimal.ZERO : kidsDue;
        this.totalDue = this.frameDue.add(this.consumableDue).add(this.kidsDue);
    }

    public BigDecimal getFrameDue() {
        return frameDue;
    }

    public BigDecimal getConsumableDue() {
        return consumableDue;
    }

    public BigDecimal getKidsDue() {
        return kidsDue;
    }

    public BigDecimal getTotalDue() {
        return totalDue;
    }
}
