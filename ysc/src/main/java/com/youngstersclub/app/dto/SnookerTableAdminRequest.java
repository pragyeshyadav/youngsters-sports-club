package com.youngstersclub.app.dto;

import java.math.BigDecimal;

public class SnookerTableAdminRequest {

    private String tableName;
    private BigDecimal ratePerMinute;

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public BigDecimal getRatePerMinute() {
        return ratePerMinute;
    }

    public void setRatePerMinute(BigDecimal ratePerMinute) {
        this.ratePerMinute = ratePerMinute;
    }
}
