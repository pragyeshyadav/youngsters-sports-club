package com.youngstersclub.app.dto;

import java.math.BigDecimal;

public class WhatsappTemplateExecutionRecipientDto {
    private final Integer userId;
    private final String name;
    private final String phone;
    private final BigDecimal amount;

    public WhatsappTemplateExecutionRecipientDto(Integer userId, String name, String phone, BigDecimal amount) {
        this.userId = userId;
        this.name = name;
        this.phone = phone;
        this.amount = amount;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
