package com.youngstersclub.app.dto;

public class MessageResponseDto {
    private final String message;

    public MessageResponseDto(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
