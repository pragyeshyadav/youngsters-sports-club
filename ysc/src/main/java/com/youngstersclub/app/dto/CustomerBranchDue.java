package com.youngstersclub.app.dto;

import java.math.BigDecimal;

public record CustomerBranchDue(
        Long customerId,
        Long branchId,
        BigDecimal frameDue,
        BigDecimal consumableDue,
        BigDecimal kidsPlayDue,
        BigDecimal gameActivityDue,
        BigDecimal totalDue) {

    public CustomerBranchDue {
        frameDue = normalize(frameDue);
        consumableDue = normalize(consumableDue);
        kidsPlayDue = normalize(kidsPlayDue);
        gameActivityDue = normalize(gameActivityDue);
        totalDue = normalize(totalDue);
    }

    private static BigDecimal normalize(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
