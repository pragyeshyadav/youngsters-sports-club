package com.youngstersclub.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.youngstersclub.app.dto.WhatsAppMessageStatusPageDto;
import com.youngstersclub.app.dto.WhatsAppTrackingMetadata;
import java.time.LocalDate;
import java.time.LocalDateTime;

public interface WhatsAppMessageStatusStore {

    void trackAccepted(WhatsAppTrackingMetadata metadata, String wamid, LocalDateTime sentTime);

    void trackNotAccepted(
            WhatsAppTrackingMetadata metadata,
            LocalDateTime sentTime,
            Integer metaErrorCode,
            String metaErrorMessage);

    void applyWebhookPayload(JsonNode payload);

    WhatsAppMessageStatusPageDto getMessagesForOrganizationOnDate(
            Long organizationId,
            Long branchId,
            LocalDate date,
            int page,
            int pageSize);
}
