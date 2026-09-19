package com.youngstersclub.app.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ClubNotificationFailureReport(
        String broadcastId,
        Long organizationId,
        String organizationName,
        String templateName,
        String notificationMessage,
        LocalDateTime triggeredAt,
        LocalDateTime finalizedAt,
        int acceptedCount,
        int failedCount,
        int completedCount,
        int pendingCount,
        boolean timedOut,
        List<ClubNotificationFailureReason> failureReasons) {
}
