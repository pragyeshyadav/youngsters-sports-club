package com.youngstersclub.app.dto;

public record ClubNotificationFailureReason(
        Integer code,
        String title,
        String message,
        String details,
        int affectedCount) {
}
