package com.youngstersclub.app.dto;

public class ConsumableStockCreateResponseDto {
    private final Long stockEntryId;
    private final String message;

    public ConsumableStockCreateResponseDto(Long stockEntryId, String message) {
        this.stockEntryId = stockEntryId;
        this.message = message;
    }

    public Long getStockEntryId() {
        return stockEntryId;
    }

    public String getMessage() {
        return message;
    }
}
