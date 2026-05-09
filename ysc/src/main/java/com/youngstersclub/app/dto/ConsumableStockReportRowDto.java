package com.youngstersclub.app.dto;

public class ConsumableStockReportRowDto {
    private final Long itemId;
    private final String itemName;
    private final Long stockAdded;
    private final Long soldQuantity;
    private final Long availableStock;

    public ConsumableStockReportRowDto(
            Long itemId,
            String itemName,
            Long stockAdded,
            Long soldQuantity,
            Long availableStock) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.stockAdded = stockAdded;
        this.soldQuantity = soldQuantity;
        this.availableStock = availableStock;
    }

    public Long getItemId() {
        return itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public Long getStockAdded() {
        return stockAdded;
    }

    public Long getSoldQuantity() {
        return soldQuantity;
    }

    public Long getAvailableStock() {
        return availableStock;
    }
}
