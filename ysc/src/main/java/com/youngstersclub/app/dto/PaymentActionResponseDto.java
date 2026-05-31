package com.youngstersclub.app.dto;

public class PaymentActionResponseDto {
    private final String status;
    private final String message;

    public PaymentActionResponseDto(String status, String message) {
        this.status = status;
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
