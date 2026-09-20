package com.youngstersclub.app.dto;

public record ClubNotificationBroadcastSummary(
        String recipientType,
        String message,
        int originalCandidates,
        int healthExcludedRecipients,
        int eligibleRecipients,
        int attemptedRecipients,
        int acceptedByMeta,
        int immediateSendFailures,
        int uncertainRecipients) {
}
