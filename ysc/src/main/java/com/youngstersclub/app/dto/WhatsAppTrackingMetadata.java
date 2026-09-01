package com.youngstersclub.app.dto;

public record WhatsAppTrackingMetadata(
        Long organizationId,
        Long branchId,
        String branchName,
        Integer userId,
        String customerName,
        String customerPhone,
        String templateName) {
}
