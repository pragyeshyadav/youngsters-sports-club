package com.youngstersclub.app.dto;

import com.youngstersclub.app.enums.ExpenseType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class BranchExpenseDto {
    private final Long id;
    private final String expenseName;
    private final BigDecimal amount;
    private final ExpenseType expenseType;
    private final LocalDate expenseDate;
    private final String notes;
    private final Integer paidByUserId;
    private final String paidByName;
    private final Integer createdByUserId;
    private final String createdByName;
    private final Long branchId;
    private final String branchName;
    private final LocalDateTime createdAt;

    public BranchExpenseDto(
            Long id,
            String expenseName,
            BigDecimal amount,
            ExpenseType expenseType,
            LocalDate expenseDate,
            String notes,
            Integer paidByUserId,
            String paidByName,
            Integer createdByUserId,
            String createdByName,
            Long branchId,
            String branchName,
            LocalDateTime createdAt) {
        this.id = id;
        this.expenseName = expenseName;
        this.amount = amount == null ? BigDecimal.ZERO : amount;
        this.expenseType = expenseType;
        this.expenseDate = expenseDate;
        this.notes = notes;
        this.paidByUserId = paidByUserId;
        this.paidByName = paidByName;
        this.createdByUserId = createdByUserId;
        this.createdByName = createdByName;
        this.branchId = branchId;
        this.branchName = branchName;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getExpenseName() {
        return expenseName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public ExpenseType getExpenseType() {
        return expenseType;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public String getNotes() {
        return notes;
    }

    public Integer getPaidByUserId() {
        return paidByUserId;
    }

    public String getPaidByName() {
        return paidByName;
    }

    public Integer getCreatedByUserId() {
        return createdByUserId;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public Long getBranchId() {
        return branchId;
    }

    public String getBranchName() {
        return branchName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
