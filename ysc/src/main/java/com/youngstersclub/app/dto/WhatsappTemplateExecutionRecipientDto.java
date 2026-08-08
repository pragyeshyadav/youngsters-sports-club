package com.youngstersclub.app.dto;

import java.math.BigDecimal;

public class WhatsappTemplateExecutionRecipientDto {
    private final Integer userId;
    private final String name;
    private final String phone;
    private final BigDecimal amount;
    private final String detail;
    private final String organizationName;
    private final String branchName;
    private final String status;

    public WhatsappTemplateExecutionRecipientDto(Integer userId, String name, String phone, BigDecimal amount) {
        this(userId, name, phone, amount, null, null, null, null);
    }

    public WhatsappTemplateExecutionRecipientDto(Integer userId, String name, String phone, BigDecimal amount, String detail) {
        this(userId, name, phone, amount, detail, null, null, null);
    }

    public WhatsappTemplateExecutionRecipientDto(
            Integer userId,
            String name,
            String phone,
            BigDecimal amount,
            String detail,
            String organizationName,
            String branchName,
            String status) {
        this.userId = userId;
        this.name = name;
        this.phone = phone;
        this.amount = amount;
        this.detail = detail;
        this.organizationName = organizationName;
        this.branchName = branchName;
        this.status = status;
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

    public String getDetail() {
        return detail;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public String getBranchName() {
        return branchName;
    }

    public String getStatus() {
        return status;
    }
}
