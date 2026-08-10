package com.youngstersclub.app.dto;

public class ExpensePayerOptionDto {
    private final Integer userId;
    private final String name;
    private final String role;

    public ExpensePayerOptionDto(Integer userId, String name, String role) {
        this.userId = userId;
        this.name = name;
        this.role = role;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }
}
